package de.servicehealth.epa4all.server.idp;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.brainPoolExtension.BrainpoolCurves;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.data.AuthorizationRequest;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.gematik.idp.field.ClaimName;
import de.gematik.idp.field.CodeChallengeMethod;
import de.health.service.cetp.CertificateInfo;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.cetp.KonnektorClient;
import de.servicehealth.epa4all.server.idp.action.AuthAction;
import de.servicehealth.epa4all.server.idp.action.LoginAction;
import de.servicehealth.epa4all.server.idp.action.VauNpAction;
import de.servicehealth.epa4all.server.idp.func.IdpFunc;
import de.servicehealth.epa4all.server.serviceport.IKonnektorAPI;
import de.servicehealth.epa4all.server.serviceport.MultiKonnektorService;
import de.servicehealth.model.SendAuthCodeSCtype;
import de.servicehealth.startup.StartableService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import kong.unirest.core.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jose4j.jwt.JwtClaims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static de.servicehealth.epa4all.server.idp.utils.IdpUtils.getSignedJwt;

@ApplicationScoped
public class IdpClient extends StartableService {

    private final static Logger log = LoggerFactory.getLogger(IdpClient.class.getName());

    public static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();

    static {
        Security.removeProvider(BOUNCY_CASTLE_PROVIDER.getName());
        Security.insertProviderAt(BOUNCY_CASTLE_PROVIDER, 1);
    }

    IdpConfig idpConfig;
    ManagedExecutor managedExecutor;
    KonnektorClient konnektorClient;
    AuthenticatorClient authenticatorClient;
    MultiKonnektorService multiKonnektorService;

    private DiscoveryDocumentResponse discoveryDocumentResponse;

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    public IdpClient(
        IdpConfig idpConfig,
        ManagedExecutor managedExecutor,
        KonnektorClient konnektorClient,
        AuthenticatorClient authenticatorClient,
        MultiKonnektorService multiKonnektorService
    ) {
        this.idpConfig = idpConfig;
        this.managedExecutor = managedExecutor;
        this.konnektorClient = konnektorClient;
        this.authenticatorClient = authenticatorClient;
        this.multiKonnektorService = multiKonnektorService;
    }

    public void onStart() throws Exception {
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
        BrainpoolCurves.init();
        retrieveDiscoveryDocument();
    }

    private void retrieveDiscoveryDocument() throws Exception {
        DiscoveryDocumentFile<DiscoveryDocumentWrapper> documentFile = new DiscoveryDocumentFile<>(configDirectory);
        DiscoveryDocumentWrapper documentWrapper = documentFile.load();
        if (documentWrapper != null) {
            discoveryDocumentResponse = documentWrapper.toDiscoveryDocumentResponse();
            return;
        }

        boolean worked = false;
        while (!worked) {
            try {
                log.info("Downloading: " + idpConfig.getDiscoveryDocumentUrl());
                discoveryDocumentResponse = authenticatorClient.retrieveDiscoveryDocument(
                    idpConfig.getDiscoveryDocumentUrl(), Optional.empty()
                );
                DiscoveryDocumentWrapper wrapper = new DiscoveryDocumentWrapper(discoveryDocumentResponse);
                new DiscoveryDocumentFile<>(configDirectory).store(wrapper);
                worked = true;
            } catch (Exception ex) {
                log.error("Could not read discovery document. Trying again in 10 seconds.", ex);
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    log.error("Could not wait.", e);
                }
            }
        }
    }

    public void getAuthCode(
        String nonce,
        URI location,
        String smcbHandle,
        UserRuntimeConfig runtimeConfig,
        Consumer<SendAuthCodeSCtype> authCodeConsumer
    ) {
        IKonnektorAPI servicePorts = multiKonnektorService.getServicePorts(runtimeConfig);
        IdpFunc idpFunc = IdpFunc.init(servicePorts);
        VauNpAction authAction = new VauNpAction(
            authenticatorClient,
            discoveryDocumentResponse,
            authCodeConsumer,
            idpFunc
        );
        processVauNPAction(nonce, location, smcbHandle, runtimeConfig, authAction, idpFunc);
    }

    public SendAuthCodeSCtype getAuthCodeSync(
        String nonce,
        URI location,
        String smcbHandle,
        UserRuntimeConfig runtimeConfig
    ) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        ThreadLocal<SendAuthCodeSCtype> threadLocalAuthCode = new ThreadLocal<>();
        IdpFunc idpFunc = IdpFunc.init(multiKonnektorService.getServicePorts(runtimeConfig));
        VauNpAction authAction = new VauNpAction(
            authenticatorClient,
            discoveryDocumentResponse,
            (s) -> {
                threadLocalAuthCode.set(s);
                countDownLatch.countDown();
            },
            idpFunc
        );
        processVauNPAction(nonce, location, smcbHandle, runtimeConfig, authAction, idpFunc);
        countDownLatch.await();
        return threadLocalAuthCode.get();
    }

    private void processVauNPAction(
        String nonce,
        URI location,
        String smcbHandle,
        UserRuntimeConfig userRuntimeConfig,
        AuthAction authAction,
        IdpFunc idpFunc
    ) {
        CertificateInfo certificateInfo = konnektorClient.getSmcbX509Certificate(userRuntimeConfig, smcbHandle);

        JwtClaims claims = new JwtClaims();
        claims.setClaim(ClaimName.NONCE.getJoseName(), nonce);
        claims.setClaim(ClaimName.ISSUED_AT.getJoseName(), System.currentTimeMillis() / 1000);
        claims.setClaim(ClaimName.EXPIRES_AT.getJoseName(), (System.currentTimeMillis() / 1000) + 1200);

        // A_24882-01 - Signatur clientAttest
        X509Certificate smcbAuthCert = certificateInfo.getCertificate();
        String clientAttest = getSignedJwt(
            smcbAuthCert,
            claims,
            certificateInfo.getSignatureType(),
            smcbHandle,
            true,
            idpFunc
        );

        // A_24760 - Start der Nutzerauthentifizierung
        // Parse query string into map
        Map<String, String> queryMap = new HashMap<>();
        String query = location.getQuery();
        Arrays.stream(query.split("&")).map(s -> s.split("=")).forEach(s -> queryMap.put(s[0], s[1]));
        sendAuthorizationRequest(
            smcbHandle,
            queryMap,
            smcbAuthCert,
            clientAttest,
            certificateInfo.getSignatureType(),
            authAction
        );
    }

    // A_24944-01 - Anfrage des "AUTHORIZATION_CODE" für ein "ID_TOKEN"
    private void sendAuthorizationRequest(
        String smcbHandle,
        Map<String, String> queryMap,
        X509Certificate smcbAuthCert,
        String clientAttest,
        String signatureType,
        AuthAction authAction
    ) {
        String codeChallenge = queryMap.get("code_challenge");
        AuthorizationRequest authorizationRequest = AuthorizationRequest.builder()
            .link(discoveryDocumentResponse.getAuthorizationEndpoint())
            .clientId(queryMap.get("client_id"))
            .codeChallenge(codeChallenge)
            .codeChallengeMethod(CodeChallengeMethod.valueOf(queryMap.get("code_challenge_method")))
            .redirectUri(queryMap.get("redirect_uri"))
            .state(queryMap.get("state"))
            .scopes(Set.of(queryMap.get("scope").replace("+", " ")))
            .nonce(queryMap.get("nonce"))
            .build();

        authenticatorClient.doAuthorizationRequest(
            authorizationRequest,
            UnaryOperator.identity(),
            (HttpResponse<AuthenticationChallenge> authenticationChallenge) -> {
                authAction.execute(
                    authenticationChallenge.getBody(),
                    smcbAuthCert,
                    codeChallenge,
                    smcbHandle,
                    clientAttest,
                    signatureType
                );
            }
        );
    }

    /**
     * Content for PS originated entitlements:</br>
     * - protected_header contains:
     * - "typ": "JWT"
     * - "alg": "ES256" or "PS256"
     * - "x5c": signature certificate (C.HCI.AUT from smc-b of requestor)
     * - payload claims:
     * - "iat": issued at timestamp
     * - "exp": expiry timestamp (always iat + 20min)
     * - "auditEvidence": proof-of-audit received from VSDM Service ('Prüfziffer des VSDM Prüfungsnachweises')
     * - signature contains token signature
     * example: "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsIng1YyI6WyJNSUlEeXpDQ0EzS2dBd0lCQWdJSEFRMnAvOXp2SXpBS0JnZ3Foa2pPUFFRREFqQ0JtVEVMTUFrR0ExVUVCaE1DUkVVeEh6QWRCZ05WQkFvTUZtZGxiV0YwYVdzZ1IyMWlTQ0JPVDFRdFZrRk1TVVF4U0RCR0JnTlZCQXNNUDBsdWMzUnBkSFYwYVc5dUlHUmxjeUJIWlhOMWJtUm9aV2wwYzNkbGMyVnVjeTFEUVNCa1pYSWdWR1ZzWlcxaGRHbHJhVzVtY21GemRISjFhM1IxY2pFZk1CMEdBMVVFQXd3V1IwVk5MbE5OUTBJdFEwRTVJRlJGVTFRdFQwNU1XVEFlRncweU1EQXhNalF3TURBd01EQmFGdzB5TkRFeU1URXlNelU1TlRsYU1JSGZNUXN3Q1FZRFZRUUdFd0pFUlRFVE1CRUdBMVVFQnd3S1I4TzJkSFJwYm1kbGJqRU9NQXdHQTFVRUVRd0ZNemN3T0RNeEhEQWFCZ05WQkFrTUUwUmhibnBwWjJWeUlGTjBjbUhEbjJVZ01UTXhLakFvQmdOVkJBb01JVE10VTAxRExVSXRWR1Z6ZEd0aGNuUmxMVGc0TXpFeE1EQXdNREV4TmpNMU1qRWRNQnNHQTFVRUJSTVVPREF5TnpZNE9ETXhNVEF3TURBeE1UWXpOVEl4RVRBUEJnTlZCQVFNQ0U1MWJHeHRZWGx5TVE4d0RRWURWUVFxREFaS2RXeHBZVzR4SGpBY0JnTlZCQU1NRlVKaFpDQkJjRzkwYUdWclpWUkZVMVF0VDA1TVdUQmFNQlFHQnlxR1NNNDlBZ0VHQ1Nza0F3TUNDQUVCQndOQ0FBUWU5bmE1VDEyOGNmOGI4VTVkVlYzdGpBQk1QdkttZHIzYVRjRTZwU1ZGdUtGTXJIM3RnYVhoN2pNVHhiOEg3ZVZ5bUtyc2lLUGlJZ2xCK0F2UEFTaXVvNElCV2pDQ0FWWXdEQVlEVlIwVEFRSC9CQUl3QURBNEJnZ3JCZ0VGQlFjQkFRUXNNQ293S0FZSUt3WUJCUVVITUFHR0hHaDBkSEE2THk5bGFHTmhMbWRsYldGMGFXc3VaR1V2YjJOemNDOHdFd1lEVlIwbEJBd3dDZ1lJS3dZQkJRVUhBd0l3SHdZRFZSMGpCQmd3Rm9BVVlvaWF4Tjc4by9PVE9jdWZrT2NUbWoySnpIVXdIUVlEVlIwT0JCWUVGQTJZR1B4RTJYcUhlYUZSSURRRDRleXR6d0xGTUE0R0ExVWREd0VCL3dRRUF3SUhnREFnQmdOVkhTQUVHVEFYTUFvR0NDcUNGQUJNQklFak1Ba0dCeXFDRkFCTUJFMHdnWVFHQlNza0NBTURCSHN3ZWFRb01DWXhDekFKQmdOVkJBWVRBa1JGTVJjd0ZRWURWUVFLREE1blpXMWhkR2xySUVKbGNteHBiakJOTUVzd1NUQkhNQmNNRmNPV1ptWmxiblJzYVdOb1pTQkJjRzkwYUdWclpUQUpCZ2NxZ2hRQVRBUTJFeUV6TFZOTlF5MUNMVlJsYzNScllYSjBaUzA0T0RNeE1UQXdNREF4TVRZek5USXdDZ1lJS29aSXpqMEVBd0lEUndBd1JBSWdBMStLWERpWXkyWTBXdkFjUk5URzRmNkNaaVBQSndiWlBrTmJnNUU3ekVVQ0lBYVU0MEFLMmxpVGZMSGkrSjZERCtIVWVLUEdaVGh4OUhwbVFybHJtbjhqIl19"
     */
    public String createEntitlementPSJWT(
        String smcbHandle,
        String auditEvidence,
        String hcv,
        UserRuntimeConfig userRuntimeConfig
    ) {
        IKonnektorAPI servicePorts = multiKonnektorService.getServicePorts(userRuntimeConfig);
        JwtClaims claims = new JwtClaims();
        claims.setClaim(ClaimName.ISSUED_AT.getJoseName(), System.currentTimeMillis() / 1000);
        claims.setClaim(ClaimName.EXPIRES_AT.getJoseName(), (System.currentTimeMillis() / 1000) + 1200);
        claims.setClaim("auditEvidence", auditEvidence);
        if (idpConfig.hcvEnabled) {
            log.info("idpConfig.hcvEnabled=true, hcv=" + hcv);
            claims.setClaim("hcv", hcv);
        } else {
            log.info("idpConfig.hcvEnabled=false");
        }
        CertificateInfo certificateInfo = konnektorClient.getSmcbX509Certificate(userRuntimeConfig, smcbHandle);
        return getSignedJwt(
            certificateInfo.getCertificate(),
            claims,
            certificateInfo.getSignatureType(),
            smcbHandle,
            true,
            IdpFunc.init(servicePorts)
        );
    }

    public void getBearerToken(
        UserRuntimeConfig userRuntimeConfig,
        Consumer<String> bearerConsumer
    ) throws Exception {
        IKonnektorAPI servicePorts = multiKonnektorService.getServicePorts(userRuntimeConfig);
        IdpFunc idpFunc = IdpFunc.init(servicePorts);
        LoginAction authAction = new LoginAction(
            idpConfig.getClientId(),
            idpConfig.getAuthRequestRedirectUrl(),
            authenticatorClient,
            discoveryDocumentResponse,
            bearerConsumer,
            idpFunc
        );
        processBearerAction(userRuntimeConfig, authAction);
    }

    public void processBearerAction(
        UserRuntimeConfig userRuntimeConfig,
        AuthAction authAction
    ) throws Exception {

    }
}