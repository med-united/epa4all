package de.servicehealth.epa4all.idp;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.data.AuthenticationRequest;
import de.gematik.idp.client.data.AuthenticationResponse;
import de.gematik.idp.client.data.AuthorizationRequest;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.gematik.idp.field.ClaimName;
import de.gematik.idp.field.CodeChallengeMethod;
import de.gematik.idp.token.IdpJwe;
import de.gematik.idp.token.JsonWebToken;
import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureServicePortType;
import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.FaultMessage;
import de.gematik.ws.conn.cardservice.v8.VerifyPin;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.CardServicePortType;
import de.gematik.ws.conn.cardservicecommon.v2.CardTypeType;
import de.gematik.ws.conn.certificateservice.v6.CryptType;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificate;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificate.CertRefList;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificateResponse;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.certificateservicecommon.v2.CertRefEnum;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.v7.GetCards;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;
import de.gematik.ws.conn.signatureservice.v7.BinaryDocumentType;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticate;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticateResponse;
import de.servicehealth.epa4all.config.EpaConfig;
import de.servicehealth.epa4all.config.KonnektorConfig;
import de.servicehealth.epa4all.config.KonnektorDefaultConfig;
import de.servicehealth.epa4all.config.api.IUserConfigurations;
import de.servicehealth.epa4all.idp.authorization.AuthorizationSmcBApi;
import de.servicehealth.model.SendAuthCodeSC200Response;
import de.servicehealth.model.SendAuthCodeSCtype;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import kong.unirest.core.HttpResponse;
import oasis.names.tc.dss._1_0.core.schema.Base64Data;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

import static de.gematik.idp.brainPoolExtension.BrainpoolAlgorithmSuiteIdentifiers.BRAINPOOL256_USING_SHA256;
import static org.jose4j.jws.AlgorithmIdentifiers.RSA_PSS_USING_SHA256;
import static org.jose4j.jws.EcdsaUsingShaAlgorithm.convertDerToConcatenated;

@ApplicationScoped
public class IdpClient {

    private final static Logger log = Logger.getLogger(IdpClient.class.getName());

    private static final String URN_BSI_TR_03111_ECDSA = "urn:bsi:tr:03111:ecdsa";

    private static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();

    static {
        java.security.Security.insertProviderAt(BOUNCY_CASTLE_PROVIDER, 1);
    }

    private final String userAgent = "ServiceHealth/1.0";

    IdpConfig idpConfig;
    EpaConfig epaConfig;
    ServicePortProvider servicePortProvider;
    AuthenticatorClient authenticatorClient;
    AuthorizationSmcBApi authorizationService;
    KonnektorDefaultConfig konnektorDefaultConfig;

    private final DiscoveryDocumentResponse discoveryDocumentResponse;

    private AuthSignatureServicePortType authSignatureServicePortType;
    private CardServicePortType cardServicePortType;

    @Inject
    public IdpClient(
        IdpConfig idpConfig,
        EpaConfig epaConfig,
        ServicePortProvider servicePortProvider,
        AuthenticatorClient authenticatorClient,
        AuthorizationSmcBApi authorizationService,
        KonnektorDefaultConfig konnektorDefaultConfig
    ) {
        this.idpConfig = idpConfig;
        this.epaConfig = epaConfig;
        this.servicePortProvider = servicePortProvider;
        this.authenticatorClient = authenticatorClient;
        this.authorizationService = authorizationService;
        this.konnektorDefaultConfig = konnektorDefaultConfig;

        discoveryDocumentResponse = authenticatorClient.retrieveDiscoveryDocument(
            idpConfig.getDiscoveryDocumentUrl(), Optional.empty()
        );
    }

    private String getOrDefault(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }

    public ContextType getContextType(KonnektorConfig konnektorConfig) {
        ContextType contextType = new ContextType();
        IUserConfigurations userConfigurations = konnektorConfig.getUserConfigurations();
        contextType.setMandantId(getOrDefault(userConfigurations.getMandantId(), konnektorDefaultConfig.getMandantId()));
        contextType.setClientSystemId(getOrDefault(userConfigurations.getClientSystemId(), konnektorDefaultConfig.getClientSystemId()));
        contextType.setWorkplaceId(getOrDefault(userConfigurations.getWorkplaceId(), konnektorDefaultConfig.getWorkplaceId()));
        contextType.setUserId(getOrDefault(userConfigurations.getUserId(), konnektorDefaultConfig.getUserId().orElse(null)));
        return contextType;
    }

    public void getVauNp(KonnektorConfig konnektorConfig, Consumer<String> vauNPConsumer) throws Exception {
        ContextType contextType = getContextType(konnektorConfig);

        // TODO Map<SimpleUserConfig, SingleConnectorServicesProvider> singleConnectorServicesProvider

        cardServicePortType = servicePortProvider.getCardServicePortType(konnektorConfig);
        authSignatureServicePortType = servicePortProvider.getAuthSignatureServicePortType(konnektorConfig);
        CertificateServicePortType certificateServicePortType = servicePortProvider.getCertificateServicePort(konnektorConfig);
        EventServicePortType eventServicePort = servicePortProvider.getEventServicePort(konnektorConfig);

        GetCards getCards = new GetCards();
        getCards.setContext(contextType);
        getCards.setCardType(CardTypeType.SMC_B);
        String smcbHandle = eventServicePort.getCards(getCards).getCards().getCard().get(0).getCardHandle();

        // A_24881 - Nonce anfordern für Erstellung "Attestation der Umgebung"
        String nonce = authorizationService.getNonce(userAgent).getNonce();

        // A_20666-02 - Auslesen des Authentisierungszertifikates 
        ReadCardCertificate readCardCertificateRequest = new ReadCardCertificate();
        CertRefList certRefList = new CertRefList();
        certRefList.getCertRef().add(CertRefEnum.C_AUT);
        readCardCertificateRequest.setCertRefList(certRefList);
        readCardCertificateRequest.setCardHandle(smcbHandle);
        readCardCertificateRequest.setContext(contextType);
        ReadCardCertificateResponse readCardCertificateResponse = null;
        // A_24883-02 - clientAttest als ECDSA-Signatur
        String signatureType = URN_BSI_TR_03111_ECDSA;
        try {
            readCardCertificateRequest.setCrypt(CryptType.ECC);
            readCardCertificateResponse = certificateServicePortType.readCardCertificate(readCardCertificateRequest);
        } catch (de.gematik.ws.conn.certificateservice.wsdl.v6_0.FaultMessage e) {
            // Zugriffsbedingungen nicht erfüllt
            boolean code4085 = e.getFaultInfo().getTrace().stream().anyMatch(t ->
                t.getCode().equals(BigInteger.valueOf(4085L))
            );

            if (code4085) {
                try {
                    verifyPin(contextType, smcbHandle);
                    // try again
                    getVauNp(konnektorConfig, vauNPConsumer);
                } catch (de.gematik.ws.conn.cardservice.wsdl.v8_1.FaultMessage e2) {
                    throw new RuntimeException("Could not verify pin", e2);
                }
            } else {
                readCardCertificateRequest.setCrypt(CryptType.RSA);
                try {
                    readCardCertificateResponse = certificateServicePortType.readCardCertificate(readCardCertificateRequest);
                    // A_24884-01 - clientAttest signieren als PKCS#1-Signatur 
                    signatureType = "urn:ietf:rfc:3447";
                } catch (de.gematik.ws.conn.certificateservice.wsdl.v6_0.FaultMessage e1) {
                    throw new RuntimeException("Could not external authenticate", e1);
                }
            }
        }

        if (readCardCertificateResponse == null) {
            throw new RuntimeException("Could not read card certificate");
        }

        byte[] x509Certificate = readCardCertificateResponse
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
        String clientAttest = getSignedJwt(contextType, smcbHandle, smcbAuthCert, signatureType, claims, true);

        // A_24760 - Start der Nutzerauthentifizierung
        try (Response response = authorizationService.sendAuthorizationRequestSCWithResponse(userAgent)) {
            // Parse query string into map
            Map<String, String> queryMap = new HashMap<>();
            String query = response.getLocation().getQuery();
            Arrays.stream(query.split("&")).map(s -> s.split("=")).forEach(s -> queryMap.put(s[0], s[1]));
            sendAuthorizationRequest(contextType, smcbHandle, queryMap, smcbAuthCert, clientAttest, signatureType, vauNPConsumer);
        }
    }

    // A_24944-01 - Anfrage des "AUTHORIZATION_CODE" für ein "ID_TOKEN"
    private void sendAuthorizationRequest(
        ContextType contextType,
        String smcbHandle,
        Map<String, String> queryMap,
        X509Certificate smcbAuthCert,
        String clientAttest,
        String signatureType,
        Consumer<String> vauNPConsumer
    ) {
        AuthorizationRequest authorizationRequest = AuthorizationRequest.builder()
            .link(discoveryDocumentResponse.getAuthorizationEndpoint())
            .clientId(queryMap.get("client_id"))
            .codeChallenge(queryMap.get("code_challenge"))
            .codeChallengeMethod(CodeChallengeMethod.valueOf(queryMap.get("code_challenge_method")))
            .redirectUri(queryMap.get("redirect_uri"))
            .state(queryMap.get("state"))
            .scopes(Set.of(queryMap.get("scope").replace("+", " ")))
            .nonce(queryMap.get("nonce"))
            .build();

        authenticatorClient.doAuthorizationRequest(authorizationRequest, UnaryOperator.identity(), (HttpResponse<AuthenticationChallenge> authenticationChallenge) -> {
            AuthenticationResponse authenticationResponse = processAuthenticationChallenge(
                contextType, smcbHandle, authenticationChallenge, smcbAuthCert, signatureType
            );
            SendAuthCodeSCtype sendAuthCodeSC = new SendAuthCodeSCtype();
            sendAuthCodeSC.setAuthorizationCode(authenticationResponse.getCode());
            sendAuthCodeSC.setClientAttest(clientAttest);
            SendAuthCodeSC200Response sendAuthCodeSC200Response = authorizationService.sendAuthCodeSC(userAgent, sendAuthCodeSC);
            vauNPConsumer.accept(sendAuthCodeSC200Response.getVauNp());
        });
    }

    // A_20662 - Annahme des "user_consent" und des "CHALLENGE_TOKEN" 

    private AuthenticationResponse processAuthenticationChallenge(
        ContextType contextType,
        String smcbHandle,
        HttpResponse<AuthenticationChallenge> authenticationChallenge,
        X509Certificate smcbAuthCert,
        String signatureType
    ) {
        AuthenticationChallenge body = authenticationChallenge.getBody();
        JsonWebToken jsonWebToken = body.getChallenge();
        // A_20663-01 - Prüfung der Signatur des CHALLENGE_TOKEN
        // TODO:
        // jsonWebToken.verify(discoveryDocumentResponse.getIdpSig().getPublicKey());

        // A_20665-01 - Signatur der Challenge des IdP-Dienstes 
        String signedChallenge = signServerChallenge(
            contextType, smcbHandle, body.getChallenge().getRawString(), smcbAuthCert, signatureType
        );

        IdpJwe idpJwe = new IdpJwe(signedChallenge);
        AuthenticationRequest authenticationRequest = AuthenticationRequest.builder()
            .authenticationEndpointUrl(discoveryDocumentResponse.getAuthorizationEndpoint())
            .signedChallenge(idpJwe)
            .build();

        AuthenticationResponse authenticationResponse = authenticatorClient.performAuthentication(
            authenticationRequest, UnaryOperator.identity(), o -> {
            }
        );


        // TODO use commented code for login
        // String codeVerifier = Base64.getUrlEncoder().withoutPadding()
        //     .encodeToString(DigestUtils.sha256(RandomStringUtils.random(123)));
        //
        // TokenRequest tokenRequest = TokenRequest.builder()
        //     .tokenUrl(discoveryDocumentResponse.getTokenEndpoint())
        //     .clientId(idpClientId)
        //     .code(authenticationResponse.getCode())
        //     .ssoToken(authenticationResponse.getSsoToken())
        //     .redirectUrl(idpAuthRequestRedirectUrl)
        //     .codeVerifier(codeVerifier)
        //     .idpEnc(discoveryDocumentResponse.getIdpEnc())
        //     .build();
        //
        // IdpTokenResult idpTokenResult = authenticatorClient.retrieveAccessToken(
        //     tokenRequest, UnaryOperator.identity(), o -> {
        //     }
        // );
        // LocalDateTime validUntil = idpTokenResult.getValidUntil();
        // String bearerToken = idpTokenResult.getAccessToken().getRawString();


        return authenticationResponse;

    }

    private String signServerChallenge(
        ContextType contextType,
        String smcbHandle,
        String challengeToSign,
        X509Certificate certificate,
        String signatureType
    ) {
        return signServerChallengeAndEncrypt(contextType, smcbHandle, challengeToSign, certificate, signatureType, true);
    }

    private String signServerChallengeAndEncrypt(
        ContextType contextType,
        String smcbHandle,
        String challengeToSign,
        X509Certificate certificate,
        String signatureType,
        boolean encrypt
    ) {
        final JwtClaims claims = new JwtClaims();
        claims.setClaim(ClaimName.NESTED_JWT.getJoseName(), challengeToSign);
        JsonWebToken jsonWebToken = signClaimsAndReturnJWT(contextType, smcbHandle, certificate, signatureType, claims);
        if (encrypt) {
            IdpJwe encryptAsNjwt = jsonWebToken
                // A_20667-01 - Response auf die Challenge des Authorization-Endpunktes
                .encryptAsNjwt(discoveryDocumentResponse.getIdpEnc());
            return encryptAsNjwt.getRawString();
        } else {
            return jsonWebToken.getRawString();
        }
    }

    private JsonWebToken signClaimsAndReturnJWT(
        ContextType contextType,
        String smcbHandle,
        X509Certificate certificate,
        String signatureType,
        final JwtClaims claims
    ) {
        final String signedJwt = getSignedJwt(contextType, smcbHandle, certificate, signatureType, claims, false);
        return new JsonWebToken(signedJwt);
    }

    private String getSignedJwt(
        ContextType contextType,
        String smcbHandle,
        X509Certificate certificate,
        String signatureType,
        final JwtClaims claims,
        boolean nonce
    ) {
        String payload = claims.toJson();
        final JsonWebSignature jsonWebSignature = new JsonWebSignature();
        jsonWebSignature.setPayload(payload);
        jsonWebSignature.setHeader("typ", "JWT");
        if (!nonce) {
            jsonWebSignature.setHeader("cty", "NJWT");
            jsonWebSignature.setCertificateChainHeaderValue(certificate);
            if (certificate.getPublicKey() instanceof ECPublicKey) {
                jsonWebSignature.setAlgorithmHeaderValue(BRAINPOOL256_USING_SHA256);
            } else {
                jsonWebSignature.setAlgorithmHeaderValue(RSA_PSS_USING_SHA256);
            }
        } else {
            if (certificate.getPublicKey() instanceof ECPublicKey) {
                jsonWebSignature.setAlgorithmHeaderValue("ES256");
            } else {
                jsonWebSignature.setAlgorithmHeaderValue("PS256");
            }
            jsonWebSignature.setCertificateChainHeaderValue(certificate);
        }

        return jsonWebSignature.getHeaders().getEncodedHeader()
            + "."
            + jsonWebSignature.getEncodedPayload()
            + "."
            + Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                getSignatureBytes(
                    (byte[] challengeBytes) -> {
                        // A_24751 - Challenge signieren als ECDSA-Signatur
                        // A_24752 - Challenge signieren als PKCS#1-Signatur
                        return hashAndSignBytesWithExternalAuthenticateWithSMCB(
                            challengeBytes, signatureType, contextType, smcbHandle
                        );
                    },
                    jsonWebSignature,
                    sigData -> sigData));

    }

    private byte[] getSignatureBytes(
        final UnaryOperator<byte[]> contentSigner,
        final JsonWebSignature jsonWebSignature,
        final UnaryOperator<byte[]> signatureStripper) {
        return signatureStripper.apply(
            contentSigner.apply(
                (jsonWebSignature.getHeaders().getEncodedHeader()
                    + "."
                    + jsonWebSignature.getEncodedPayload())
                    .getBytes(StandardCharsets.UTF_8)));
    }

    private void verifyPin(ContextType contextType, String smcbHandle) throws de.gematik.ws.conn.cardservice.wsdl.v8_1.FaultMessage {
        VerifyPin verifyPin = new VerifyPin();
        verifyPin.setContext(contextType);
        verifyPin.setCardHandle(smcbHandle);
        verifyPin.setPinTyp("PIN.SMC");
        cardServicePortType.verifyPin(verifyPin);
    }

    private byte[] hashAndSignBytesWithExternalAuthenticateWithSMCB(
        byte[] inputBytes,
        String signatureType,
        ContextType contextType,
        String smcbHandle
    ) {

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Could not apply SHA-256 to signing bytes", e);
        }
        byte[] encodedhash = digest.digest(inputBytes);

        ExternalAuthenticate externalAuthenticate = new ExternalAuthenticate();
        BinaryDocumentType binaryDocumentType = new BinaryDocumentType();
        Base64Data base64Data = new Base64Data();
        base64Data.setValue(encodedhash);
        base64Data.setMimeType("application/octet-stream");
        binaryDocumentType.setBase64Data(base64Data);
        externalAuthenticate.setBinaryString(binaryDocumentType);
        externalAuthenticate.setContext(contextType);
        externalAuthenticate.setCardHandle(smcbHandle);
        ExternalAuthenticate.OptionalInputs optionalInputs = new ExternalAuthenticate.OptionalInputs();
        // A_24883-02 - clientAttest als ECDSA-Signatur
        optionalInputs.setSignatureType(signatureType);
        externalAuthenticate.setOptionalInputs(optionalInputs);
        ExternalAuthenticateResponse externalAuthenticateResponse = null;
        try {
            externalAuthenticateResponse = authSignatureServicePortType.externalAuthenticate(externalAuthenticate);
        } catch (FaultMessage e) {
            throw new RuntimeException("Could not external authenticate", e);
        }
        byte[] value = externalAuthenticateResponse.getSignatureObject().getBase64Signature().getValue();
        if (signatureType.equals(URN_BSI_TR_03111_ECDSA)) {
            try {
                return convertDerToConcatenated(value, 64);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            return value;
        }
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