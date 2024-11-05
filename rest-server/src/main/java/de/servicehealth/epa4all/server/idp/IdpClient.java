package de.servicehealth.epa4all.server.idp;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.data.AuthorizationRequest;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.gematik.idp.field.ClaimName;
import de.gematik.idp.field.CodeChallengeMethod;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.domain.eventservice.card.Card;
import de.health.service.cetp.domain.eventservice.card.CardType;
import de.health.service.cetp.domain.fault.CetpFault;
import de.health.service.config.api.UserRuntimeConfig;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.server.idp.action.AuthAction;
import de.servicehealth.epa4all.server.idp.action.LoginAction;
import de.servicehealth.epa4all.server.idp.action.VauNpAction;
import de.servicehealth.epa4all.server.idp.func.IdpFunc;
import de.servicehealth.epa4all.server.idp.func.IdpFuncer;
import de.servicehealth.epa4all.server.serviceport.IKonnektorServicePortsAPI;
import de.servicehealth.epa4all.server.serviceport.MultiKonnektorService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import kong.unirest.core.HttpResponse;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jose4j.jwt.JwtClaims;

import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.health.service.cetp.domain.eventservice.card.CardType.SMC_B;
import static de.servicehealth.epa4all.server.idp.action.AuthAction.URN_BSI_TR_03111_ECDSA;
import static de.servicehealth.epa4all.server.idp.utils.IdpUtils.getSignedJwt;

@ApplicationScoped
public class IdpClient {

    private final static Logger log = Logger.getLogger(IdpClient.class.getName());

    public static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();

    static {
    	Security.removeProvider(BOUNCY_CASTLE_PROVIDER.getName());
        Security.insertProviderAt(BOUNCY_CASTLE_PROVIDER, 1);
    }

    IdpFuncer idpFuncer;
    IdpConfig idpConfig;
    MultiEpaService multiEpaService;
    IKonnektorClient konnektorClient;
    AuthenticatorClient authenticatorClient;
    MultiKonnektorService multiKonnektorService;

    private DiscoveryDocumentResponse discoveryDocumentResponse;

    // A_24883-02 - clientAttest als ECDSA-Signatur
    private String signatureType = URN_BSI_TR_03111_ECDSA;

    @Inject ManagedExecutor managedExecutor;

    @Inject
    public IdpClient(
        IdpFuncer idpFuncer,
        IdpConfig idpConfig,
        MultiEpaService multiEpaService,
        IKonnektorClient konnektorClient,
        AuthenticatorClient authenticatorClient,
        MultiKonnektorService multiKonnektorService
    ) {
        this.idpFuncer = idpFuncer;
        this.idpConfig = idpConfig;
        this.multiEpaService = multiEpaService;
        this.konnektorClient = konnektorClient;
        this.authenticatorClient = authenticatorClient;
        this.multiKonnektorService = multiKonnektorService;
    }

    void onStart(@Observes StartupEvent ev) {
        // Do this async
        managedExecutor.submit(() -> {
            boolean worked = false;
            while(!worked) {
                try {
                    log.info("Downloading: "+idpConfig.getDiscoveryDocumentUrl());
                    discoveryDocumentResponse = authenticatorClient.retrieveDiscoveryDocument(
                        idpConfig.getDiscoveryDocumentUrl(), Optional.empty()
                    );
                    worked = true;
                } catch(Exception ex) {
                    log.log(Level.SEVERE, "Could not read discovery document. Trying again in 10 seconds.", ex);
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        log.log(Level.SEVERE, "Could not wait.", e);
                    }
                }
            }
        });
    }

    public void getVauNp(UserRuntimeConfig userRuntimeConfig, Consumer<String> vauNPConsumer) throws Exception {
        IdpFunc idpFunc = idpFuncer.init(multiEpaService.getXInsurantid(), userRuntimeConfig);
        VauNpAction authAction = new VauNpAction(
            authenticatorClient,
            discoveryDocumentResponse,
            vauNPConsumer,
            idpFunc
        );
        processVauNPAction(userRuntimeConfig, authAction, idpFunc);
    }
    
    public String getVauNpSync(UserRuntimeConfig userRuntimeConfig) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        ThreadLocal<String> threadLocalString = new ThreadLocal<String>();
        IdpFunc idpFunc = idpFuncer.init(multiEpaService.getXInsurantid(), userRuntimeConfig);
        VauNpAction authAction = new VauNpAction(
            authenticatorClient,
            discoveryDocumentResponse,
            (s) -> {
            	threadLocalString.set(s);
            	countDownLatch.countDown();
            },
            idpFunc
        );
        processVauNPAction(userRuntimeConfig, authAction, idpFunc);
        countDownLatch.await();
        return threadLocalString.get();
    }

    public void getBearerToken(UserRuntimeConfig userRuntimeConfig, Consumer<String> bearerConsumer) throws Exception {
        IdpFunc idpFunc = idpFuncer.init(multiEpaService.getXInsurantid(), userRuntimeConfig);
        LoginAction authAction = new LoginAction(
            idpConfig.getClientId(),
            idpConfig.getAuthRequestRedirectUrl(),
            authenticatorClient,
            discoveryDocumentResponse,
            bearerConsumer,
            idpFunc
        );
        processBearerAction(userRuntimeConfig, authAction, idpFuncer);
    }

    public void processBearerAction(
        UserRuntimeConfig userRuntimeConfig,
        AuthAction authAction,
        IdpFuncer idpFuncer
    ) throws Exception {

    }

    private String getSmcbHandle(UserRuntimeConfig userRuntimeConfig) throws CetpFault {
        List<Card> cards = konnektorClient.getCards(userRuntimeConfig, SMC_B);
        if(cards.size() > 1) {
        	Optional<Card> vizenzkrCard = cards.stream().filter(c -> "VincenzkrankenhausTEST-ONLY".equals(c.getCardHolderName())).findAny();
        	if(vizenzkrCard.isPresent()) {
        		return vizenzkrCard.get().getCardHandle();
        	}
        }
        return cards.getFirst().getCardHandle();
    }

    public void processVauNPAction(
        UserRuntimeConfig userRuntimeConfig,
        AuthAction authAction,
        IdpFunc idpFunc
    ) throws Exception {
        String smcbHandle = getSmcbHandle(userRuntimeConfig);

        // A_24881 - Nonce anfordern für Erstellung "Attestation der Umgebung"
        String nonce = idpFunc.getNonceSupplier().get();

        Pair<X509Certificate, Boolean> smcbAuthCertPair = konnektorClient.getSmcbX509Certificate(userRuntimeConfig, smcbHandle);
        if (smcbAuthCertPair.getValue()) {
            // A_24884-01 - clientAttest signieren als PKCS#1-Signatur
            signatureType = "urn:ietf:rfc:3447";
        }

        JwtClaims claims = new JwtClaims();
        claims.setClaim(ClaimName.NONCE.getJoseName(), nonce);
        claims.setClaim(ClaimName.ISSUED_AT.getJoseName(), System.currentTimeMillis() / 1000);
        claims.setClaim(ClaimName.EXPIRES_AT.getJoseName(), (System.currentTimeMillis() / 1000) + 1200);

        // A_24882-01 - Signatur clientAttest
        X509Certificate smcbAuthCert = smcbAuthCertPair.getKey();
        String clientAttest = getSignedJwt(
            smcbAuthCert,
            claims,
            signatureType,
            smcbHandle,
            true,
            idpFunc
        );

        // A_24760 - Start der Nutzerauthentifizierung
        try (Response response = idpFunc.getAuthorizationResponseSupplier().get()) {
            // Parse query string into map
            Map<String, String> queryMap = new HashMap<>();
            String query = response.getLocation().getQuery();
            Arrays.stream(query.split("&")).map(s -> s.split("=")).forEach(s -> queryMap.put(s[0], s[1]));
            sendAuthorizationRequest(
                smcbHandle,
                queryMap,
                smcbAuthCert,
                clientAttest,
                signatureType,
                authAction
            );
        }
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
     *   - protected_header contains:
     *     - "typ": "JWT"
     *     - "alg": "ES256" or "PS256"
     *     - "x5c": signature certificate (C.HCI.AUT from smc-b of requestor)
     *   - payload claims:
     *     - "iat": issued at timestamp
     *     - "exp": expiry timestamp (always iat + 20min)
     *     - "auditEvidence": proof-of-audit received from VSDM Service ('Prüfziffer des VSDM Prüfungsnachweises')
     *   - signature contains token signature
     *   example: "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsIng1YyI6WyJNSUlEeXpDQ0EzS2dBd0lCQWdJSEFRMnAvOXp2SXpBS0JnZ3Foa2pPUFFRREFqQ0JtVEVMTUFrR0ExVUVCaE1DUkVVeEh6QWRCZ05WQkFvTUZtZGxiV0YwYVdzZ1IyMWlTQ0JPVDFRdFZrRk1TVVF4U0RCR0JnTlZCQXNNUDBsdWMzUnBkSFYwYVc5dUlHUmxjeUJIWlhOMWJtUm9aV2wwYzNkbGMyVnVjeTFEUVNCa1pYSWdWR1ZzWlcxaGRHbHJhVzVtY21GemRISjFhM1IxY2pFZk1CMEdBMVVFQXd3V1IwVk5MbE5OUTBJdFEwRTVJRlJGVTFRdFQwNU1XVEFlRncweU1EQXhNalF3TURBd01EQmFGdzB5TkRFeU1URXlNelU1TlRsYU1JSGZNUXN3Q1FZRFZRUUdFd0pFUlRFVE1CRUdBMVVFQnd3S1I4TzJkSFJwYm1kbGJqRU9NQXdHQTFVRUVRd0ZNemN3T0RNeEhEQWFCZ05WQkFrTUUwUmhibnBwWjJWeUlGTjBjbUhEbjJVZ01UTXhLakFvQmdOVkJBb01JVE10VTAxRExVSXRWR1Z6ZEd0aGNuUmxMVGc0TXpFeE1EQXdNREV4TmpNMU1qRWRNQnNHQTFVRUJSTVVPREF5TnpZNE9ETXhNVEF3TURBeE1UWXpOVEl4RVRBUEJnTlZCQVFNQ0U1MWJHeHRZWGx5TVE4d0RRWURWUVFxREFaS2RXeHBZVzR4SGpBY0JnTlZCQU1NRlVKaFpDQkJjRzkwYUdWclpWUkZVMVF0VDA1TVdUQmFNQlFHQnlxR1NNNDlBZ0VHQ1Nza0F3TUNDQUVCQndOQ0FBUWU5bmE1VDEyOGNmOGI4VTVkVlYzdGpBQk1QdkttZHIzYVRjRTZwU1ZGdUtGTXJIM3RnYVhoN2pNVHhiOEg3ZVZ5bUtyc2lLUGlJZ2xCK0F2UEFTaXVvNElCV2pDQ0FWWXdEQVlEVlIwVEFRSC9CQUl3QURBNEJnZ3JCZ0VGQlFjQkFRUXNNQ293S0FZSUt3WUJCUVVITUFHR0hHaDBkSEE2THk5bGFHTmhMbWRsYldGMGFXc3VaR1V2YjJOemNDOHdFd1lEVlIwbEJBd3dDZ1lJS3dZQkJRVUhBd0l3SHdZRFZSMGpCQmd3Rm9BVVlvaWF4Tjc4by9PVE9jdWZrT2NUbWoySnpIVXdIUVlEVlIwT0JCWUVGQTJZR1B4RTJYcUhlYUZSSURRRDRleXR6d0xGTUE0R0ExVWREd0VCL3dRRUF3SUhnREFnQmdOVkhTQUVHVEFYTUFvR0NDcUNGQUJNQklFak1Ba0dCeXFDRkFCTUJFMHdnWVFHQlNza0NBTURCSHN3ZWFRb01DWXhDekFKQmdOVkJBWVRBa1JGTVJjd0ZRWURWUVFLREE1blpXMWhkR2xySUVKbGNteHBiakJOTUVzd1NUQkhNQmNNRmNPV1ptWmxiblJzYVdOb1pTQkJjRzkwYUdWclpUQUpCZ2NxZ2hRQVRBUTJFeUV6TFZOTlF5MUNMVlJsYzNScllYSjBaUzA0T0RNeE1UQXdNREF4TVRZek5USXdDZ1lJS29aSXpqMEVBd0lEUndBd1JBSWdBMStLWERpWXkyWTBXdkFjUk5URzRmNkNaaVBQSndiWlBrTmJnNUU3ekVVQ0lBYVU0MEFLMmxpVGZMSGkrSjZERCtIVWVLUEdaVGh4OUhwbVFybHJtbjhqIl19"
     */
    public String createEntitlementPSJWT(String auditEvidence, UserRuntimeConfig userRuntimeConfig) throws Exception {
        JwtClaims claims = new JwtClaims();
        claims.setClaim(ClaimName.ISSUED_AT.getJoseName(), System.currentTimeMillis() / 1000);
        claims.setClaim(ClaimName.EXPIRES_AT.getJoseName(), (System.currentTimeMillis() / 1000) + 1200);
        claims.setClaim("auditEvidence", auditEvidence);

        String smcbHandle = getSmcbHandle(userRuntimeConfig);
        Pair<X509Certificate, Boolean> smcbAuthCertPair = konnektorClient.getSmcbX509Certificate(userRuntimeConfig, smcbHandle);
        X509Certificate smcbAuthCert = smcbAuthCertPair.getKey();

        IdpFunc idpFunc = idpFuncer.init(multiEpaService.getXInsurantid(), userRuntimeConfig);
        return getSignedJwt(smcbAuthCert, claims, signatureType, smcbHandle, true, idpFunc);
    }
}