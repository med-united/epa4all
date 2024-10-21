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
import de.servicehealth.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.idp.action.AuthAction;
import de.servicehealth.epa4all.idp.action.LoginAction;
import de.servicehealth.epa4all.idp.action.VauNpAction;
import de.servicehealth.epa4all.idp.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.serviceport.IServicePortAggregator;
import de.servicehealth.epa4all.serviceport.MultiKonnektorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import kong.unirest.core.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.jwt.JwtClaims;

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
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.idp.action.AuthAction.URN_BSI_TR_03111_ECDSA;
import static de.servicehealth.epa4all.idp.action.AuthAction.USER_AGENT;
import static de.servicehealth.epa4all.idp.utils.IdpUtils.getSignedJwt;

@ApplicationScoped
public class IdpClient {

    private final static Logger log = Logger.getLogger(IdpClient.class.getName());

    private static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();

    static {
        Security.insertProviderAt(BOUNCY_CASTLE_PROVIDER, 1);
    }

    IdpConfig idpConfig;
    AuthenticatorClient authenticatorClient;
    AuthorizationSmcBApi authorizationService;
    MultiKonnektorService multiKonnektorService;

    private final DiscoveryDocumentResponse discoveryDocumentResponse;

    // A_24883-02 - clientAttest als ECDSA-Signatur
    private String signatureType = URN_BSI_TR_03111_ECDSA;

    @Inject
    public IdpClient(
        IdpConfig idpConfig,
        AuthenticatorClient authenticatorClient,
        AuthorizationSmcBApi authorizationService,
        MultiKonnektorService multiKonnektorService
    ) {
        this.idpConfig = idpConfig;
        this.authenticatorClient = authenticatorClient;
        this.authorizationService = authorizationService;
        this.multiKonnektorService = multiKonnektorService;

        discoveryDocumentResponse = authenticatorClient.retrieveDiscoveryDocument(
            idpConfig.getDiscoveryDocumentUrl(), Optional.empty()
        );
    }

    public void getVauNp(UserRuntimeConfig userRuntimeConfig, Consumer<String> vauNPConsumer) throws Exception {
        IServicePortAggregator servicePorts = multiKonnektorService.getServicePorts(userRuntimeConfig);
        VauNpAction authAction = new VauNpAction(
            servicePorts,
            authenticatorClient,
            authorizationService,
            discoveryDocumentResponse,
            vauNPConsumer
        );
        processAction(servicePorts, authAction);
    }

    public void getBearerToken(UserRuntimeConfig userRuntimeConfig, Consumer<String> bearerConsumer) throws Exception {
        IServicePortAggregator servicePorts = multiKonnektorService.getServicePorts(userRuntimeConfig);
        LoginAction authAction = new LoginAction(
            idpConfig.getClientId(),
            idpConfig.getAuthRequestRedirectUrl(),
            servicePorts,
            authenticatorClient,
            authorizationService,
            discoveryDocumentResponse,
            bearerConsumer
        );
        processAction(servicePorts, authAction);
    }

    private String getSmcbHandle(IServicePortAggregator servicePorts) throws Exception {
        GetCards getCards = new GetCards();
        getCards.setContext(servicePorts.getContextType());
        getCards.setCardType(CardTypeType.SMC_B);
        return servicePorts.getEventService().getCards(getCards).getCards().getCard().getFirst().getCardHandle();
    }

    private ReadCardCertificateResponse readCardCertificateResponse(
        String smcbHandle,
        IServicePortAggregator servicePorts
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
        IServicePortAggregator servicePorts,
        AuthAction authAction
    ) throws Exception {
        String smcbHandle = getSmcbHandle(servicePorts);

        // A_24881 - Nonce anfordern für Erstellung "Attestation der Umgebung"
        String nonce = authorizationService.getNonce(USER_AGENT).getNonce();

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

        JwtClaims claims = new JwtClaims();
        claims.setClaim(ClaimName.NONCE.getJoseName(), nonce);
        claims.setClaim(ClaimName.ISSUED_AT.getJoseName(), System.currentTimeMillis() / 1000);
        claims.setClaim(ClaimName.EXPIRES_AT.getJoseName(), (System.currentTimeMillis() / 1000) + 300);

        // A_24882-01 - Signatur clientAttest
        String clientAttest = getSignedJwt(servicePorts, smcbAuthCert, claims, signatureType, smcbHandle, true);

        // A_24760 - Start der Nutzerauthentifizierung
        try (Response response = authorizationService.sendAuthorizationRequestSCWithResponse(USER_AGENT)) {
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

    private void verifyPin(IServicePortAggregator servicePorts, String smcbHandle) throws FaultMessage {
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
}