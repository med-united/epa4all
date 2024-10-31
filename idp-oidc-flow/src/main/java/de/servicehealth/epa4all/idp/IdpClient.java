package de.servicehealth.epa4all.idp;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.data.AuthorizationRequest;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.gematik.idp.field.ClaimName;
import de.gematik.idp.field.CodeChallengeMethod;
import de.gematik.ws.conn.cardservice.v8.VerifyPin;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.FaultMessage;
import de.gematik.ws.conn.cardservicecommon.v2.CardTypeType;
import de.gematik.ws.conn.certificateservice.v6.CryptType;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificate;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificate.CertRefList;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificateResponse;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.certificateservicecommon.v2.CertRefEnum;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.v7.GetCards;
import de.service.health.api.epa4all.MultiEpaService;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.service.health.api.serviceport.IKonnektorServicePortsAPI;
import de.service.health.api.serviceport.MultiKonnektorService;
import de.servicehealth.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.idp.action.AuthAction;
import de.servicehealth.epa4all.idp.action.LoginAction;
import de.servicehealth.epa4all.idp.action.VauNpAction;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import kong.unirest.core.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jose4j.jwt.JwtClaims;

import com.ibm.icu.impl.duration.TimeUnit;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.idp.action.AuthAction.URN_BSI_TR_03111_ECDSA;
import static de.servicehealth.epa4all.idp.action.AuthAction.USER_AGENT;
import static de.servicehealth.epa4all.idp.utils.IdpUtils.getSignedJwt;

@ApplicationScoped
public class IdpClient {

    private final static Logger log = Logger.getLogger(IdpClient.class.getName());

    private static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();

    static {
    	Security.removeProvider(BOUNCY_CASTLE_PROVIDER.getName());
        Security.insertProviderAt(BOUNCY_CASTLE_PROVIDER, 1);
    }

    IdpConfig idpConfig;
    MultiEpaService multiEpaService;
    AuthenticatorClient authenticatorClient;
    MultiKonnektorService multiKonnektorService;

    private DiscoveryDocumentResponse discoveryDocumentResponse;

    // A_24883-02 - clientAttest als ECDSA-Signatur
    private String signatureType = URN_BSI_TR_03111_ECDSA;
    
    @Inject ManagedExecutor managedExecutor;

    @Inject
    public IdpClient(
        IdpConfig idpConfig,
        MultiEpaService multiEpaService,
        AuthenticatorClient authenticatorClient,
        MultiKonnektorService multiKonnektorService
    ) {
        this.idpConfig = idpConfig;
        this.multiEpaService = multiEpaService;
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
        IKonnektorServicePortsAPI servicePorts = multiKonnektorService.getServicePorts(userRuntimeConfig);
        VauNpAction authAction = new VauNpAction(
            multiEpaService,
            servicePorts,
            authenticatorClient,
            discoveryDocumentResponse,
            vauNPConsumer
        );
        processAction(servicePorts, authAction);
    }
    
    public String getVauNpSync(UserRuntimeConfig userRuntimeConfig) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        ThreadLocal<String> threadLocalString = new ThreadLocal<String>();
    	IKonnektorServicePortsAPI servicePorts = multiKonnektorService.getServicePorts(userRuntimeConfig);
        VauNpAction authAction = new VauNpAction(
            multiEpaService,
            servicePorts,
            authenticatorClient,
            discoveryDocumentResponse,
            (s) -> {
            	threadLocalString.set(s);
            	countDownLatch.countDown();
            }
        );
        processAction(servicePorts, authAction);
        countDownLatch.await();
        return threadLocalString.get();
    }

    public void getBearerToken(UserRuntimeConfig userRuntimeConfig, Consumer<String> bearerConsumer) throws Exception {
        IKonnektorServicePortsAPI servicePorts = multiKonnektorService.getServicePorts(userRuntimeConfig);
        LoginAction authAction = new LoginAction(
            idpConfig.getClientId(),
            idpConfig.getAuthRequestRedirectUrl(),
            multiEpaService,
            servicePorts,
            authenticatorClient,
            discoveryDocumentResponse,
            bearerConsumer
        );
        processAction(servicePorts, authAction);
    }

    private String getSmcbHandle(IKonnektorServicePortsAPI servicePorts) throws Exception {
        GetCards getCards = new GetCards();
        getCards.setContext(servicePorts.getContextType());
        getCards.setCardType(CardTypeType.SMC_B);
        return servicePorts.getEventService().getCards(getCards).getCards().getCard().getFirst().getCardHandle();
    }

    private ReadCardCertificateResponse readCardCertificateResponse(
        String smcbHandle,
        IKonnektorServicePortsAPI servicePorts
    ) throws Exception {
        // A_20666-02 - Auslesen des Authentisierungszertifikates
        ReadCardCertificate readCardCertificateRequest = new ReadCardCertificate();
        CertRefList certRefList = new CertRefList();
        certRefList.getCertRef().add(CertRefEnum.C_AUT);
        readCardCertificateRequest.setCertRefList(certRefList);
        readCardCertificateRequest.setCardHandle(smcbHandle);
        ContextType contextType = servicePorts.getContextType();
        readCardCertificateRequest.setContext(contextType);

        CertificateServicePortType certificateService = servicePorts.getCertificateService();
        try {
            readCardCertificateRequest.setCrypt(CryptType.ECC);
            return certificateService.readCardCertificate(readCardCertificateRequest);
        } catch (de.gematik.ws.conn.certificateservice.wsdl.v6_0.FaultMessage e) {
            // Zugriffsbedingungen nicht erfüllt
            boolean code4085 = e.getFaultInfo().getTrace().stream().anyMatch(t ->
                t.getCode().equals(BigInteger.valueOf(4085L))
            );
            if (code4085) {
                try {
                    verifyPin(servicePorts, smcbHandle);
                    // try again
                    readCardCertificateResponse(smcbHandle, servicePorts);
                } catch (de.gematik.ws.conn.cardservice.wsdl.v8_1.FaultMessage e2) {
                    throw new RuntimeException("Could not verify pin", e2);
                }
            } else {
                try {
                    readCardCertificateRequest.setCrypt(CryptType.RSA);
                    ReadCardCertificateResponse response = certificateService.readCardCertificate(readCardCertificateRequest);
                    // A_24884-01 - clientAttest signieren als PKCS#1-Signatur
                    signatureType = "urn:ietf:rfc:3447";
                    return response;
                } catch (de.gematik.ws.conn.certificateservice.wsdl.v6_0.FaultMessage e1) {
                    throw new RuntimeException("Could not external authenticate", e1);
                }
            }
        }
        return null;
    }

    public void processAction(
        IKonnektorServicePortsAPI servicePorts,
        AuthAction authAction
    ) throws Exception {

        // A_24881 - Nonce anfordern für Erstellung "Attestation der Umgebung"
        // TODO remove hard coded value
        AuthorizationSmcBApi authorizationSmcBApi = multiEpaService.getEpaAPI().getAuthorizationSmcBApi();
        String nonce = authorizationSmcBApi.getNonce(USER_AGENT).getNonce();

        String smcbHandle = getSmcbHandle(servicePorts);
        X509Certificate smcbAuthCert = getX09Certificate(servicePorts, smcbHandle);

        JwtClaims claims = new JwtClaims();
        claims.setClaim(ClaimName.NONCE.getJoseName(), nonce);
        claims.setClaim(ClaimName.ISSUED_AT.getJoseName(), System.currentTimeMillis() / 1000);
        claims.setClaim(ClaimName.EXPIRES_AT.getJoseName(), (System.currentTimeMillis() / 1000) + 300);

        // A_24882-01 - Signatur clientAttest
        String clientAttest = getSignedJwt(servicePorts, smcbAuthCert, claims, signatureType, smcbHandle, true);

        // A_24760 - Start der Nutzerauthentifizierung
        try (Response response = authorizationSmcBApi.sendAuthorizationRequestSCWithResponse(USER_AGENT)) {
            // Parse query string into map
            Map<String, String> queryMap = new HashMap<>();
            String query;
            if(response.getLocation() == null && response.getEntity() instanceof ByteArrayInputStream) {
            	query = new String(((ByteArrayInputStream)response.getEntity()).readAllBytes());
            	throw new RuntimeException("Should find an Location header not found. Body: "+query);
            } else {
            	query = response.getLocation().getQuery();
            }
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

	private X509Certificate getX09Certificate(IKonnektorServicePortsAPI servicePorts, String smcbHandle)
			throws Exception {
		ReadCardCertificateResponse certificateResponse = readCardCertificateResponse(smcbHandle, servicePorts);
        if (certificateResponse == null) {
            throw new RuntimeException("Could not read card certificate");
        }

        byte[] x509Certificate = certificateResponse
            .getX509DataInfoList()
            .getX509DataInfo()
            .get(0)
            .getX509Data()
            .getX509Certificate();
        X509Certificate smcbAuthCert = getCertificateFromAsn1DERCertBytes(x509Certificate);
		return smcbAuthCert;
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

    private void verifyPin(IKonnektorServicePortsAPI servicePorts, String smcbHandle) throws FaultMessage {
        VerifyPin verifyPin = new VerifyPin();
        verifyPin.setContext(servicePorts.getContextType());
        verifyPin.setCardHandle(smcbHandle);
        verifyPin.setPinTyp("PIN.SMC");
        servicePorts.getCardService().verifyPin(verifyPin);
    }

    private static X509Certificate getCertificateFromAsn1DERCertBytes(final byte[] crt) {
        try (InputStream in = new ByteArrayInputStream(crt)) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509", BOUNCY_CASTLE_PROVIDER);
            return (X509Certificate) certFactory.generateCertificate(in);
        } catch (IOException | CertificateException e) {
            throw new RuntimeException(e);
        }
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
     * @param auditEvidence
     * @return
     * @throws Exception 
     */
    public String createEntitilementPSJWT(String auditEvidence, UserRuntimeConfig userRuntimeConfig) throws Exception {
    	
    	IKonnektorServicePortsAPI servicePorts = multiKonnektorService.getServicePorts(userRuntimeConfig);
    	
    	JwtClaims claims = new JwtClaims();
        claims.setClaim(ClaimName.ISSUED_AT.getJoseName(), System.currentTimeMillis() / 1000);
        claims.setClaim(ClaimName.EXPIRES_AT.getJoseName(), (System.currentTimeMillis() / 1000) + 300);
        claims.setClaim("auditEvidence", auditEvidence);

        String smcbHandle = getSmcbHandle(servicePorts);
        X509Certificate smcbAuthCert = getX09Certificate(servicePorts, smcbHandle);

        String entitilementPSJWT = getSignedJwt(servicePorts, smcbAuthCert, claims, signatureType, smcbHandle, true);
    	
    	return entitilementPSJWT;
    }
}