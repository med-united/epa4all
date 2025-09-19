package de.servicehealth.epa4all.server.idp.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.IdpTokenResult;
import de.gematik.idp.client.data.AuthenticationRequest;
import de.gematik.idp.client.data.AuthenticationResponse;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.gematik.idp.client.data.TokenRequest;
import de.gematik.idp.token.IdpJwe;
import de.health.service.cetp.CertificateInfo;
import de.servicehealth.epa4all.server.idp.func.IdpFunc;
import org.apache.commons.codec.digest.DigestUtils;
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static de.gematik.idp.field.ClaimName.ALGORITHM;
import static de.gematik.idp.field.ClaimName.CONTENT_TYPE;
import static de.gematik.idp.field.ClaimName.EXPIRES_AT;
import static de.gematik.idp.field.ClaimName.NESTED_JWT;
import static de.health.service.cetp.CertificateInfo.URN_BSI_TR_03111_ECDSA;
import static de.servicehealth.epa4all.server.idp.utils.IdpUtils.getSignedJwt;
import static de.servicehealth.epa4all.server.idp.utils.IdpUtils.hashAndSignBytesWithExternalAuthenticateWithSMCB;

public class LoginAction extends AbstractAuthAction {

    private final String idpClientId;
    private final String idpAuthRequestRedirectUrl;
    private final Consumer<String> tokenConsumer;
    private final ObjectMapper objectMapper;

    public LoginAction(
        String idpClientId,
        String idpAuthRequestRedirectUrl,
        AuthenticatorClient authenticatorClient,
        DiscoveryDocumentResponse discoveryDocumentResponse,
        Consumer<String> tokenConsumer,
        IdpFunc idpFunc
    ) {
        super(idpFunc, authenticatorClient, discoveryDocumentResponse);

        this.idpClientId = idpClientId;
        this.idpAuthRequestRedirectUrl = idpAuthRequestRedirectUrl;
        this.tokenConsumer = tokenConsumer;

        objectMapper = new ObjectMapper();
    }

    private static String b64u(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String signCodeChallenge(
        X509Certificate certSmcb,
        String challengeToken,
        String signatureType,
        String smcbHandle
    ) throws Exception {

        boolean isCertEcc = signatureType.equals(URN_BSI_TR_03111_ECDSA);

        ObjectNode hdr = objectMapper.createObjectNode();
        hdr.put("alg", isCertEcc ? "BP256R1" : "PS256");
        hdr.put("cty", "NJWT");

        String[] smcbAuthCertB64 = { Base64.getEncoder().encodeToString(certSmcb.getEncoded()) };
        hdr.set("x5c", objectMapper.valueToTree(smcbAuthCertB64));

        String hdrJson = objectMapper.writeValueAsString(hdr);
        String hdrB64u = b64u(hdrJson.getBytes(StandardCharsets.UTF_8));

        ObjectNode pl = objectMapper.createObjectNode();
        pl.put("njwt", challengeToken);
        String plJson = objectMapper.writeValueAsString(pl);
        String plB64u = b64u(plJson.getBytes(StandardCharsets.UTF_8));

        String headerPayload = hdrB64u + "." + plB64u;
        byte[] toHash = headerPayload.getBytes(StandardCharsets.UTF_8);

        byte[] sha256 = MessageDigest.getInstance("SHA-256").digest(toHash);

        byte[] sigBytes = hashAndSignBytesWithExternalAuthenticateWithSMCB(sha256, signatureType, smcbHandle, idpFunc);

        return headerPayload + "." + b64u(sigBytes);
    }

    @Override
    public void execute(
        String epaNonce,
        String smcbHandle,
        String codeChallenge,
        CertificateInfo certificateInfo,
        AuthenticationChallenge authChallenge
    ) throws Exception {
        X509Certificate smcbAuthCert = certificateInfo.getCertificate();
        String signatureType = certificateInfo.getSignatureType();
        String challengeToken = authChallenge.getChallenge().getRawString();

        // Map<String, Object> bodyClaims = authChallenge.getChallenge().extractBodyClaims();
        // final JwtClaims claims = new JwtClaims();
        // claims.setClaim(NESTED_JWT.getJoseName(), challengeToken);
        
        // claims.setClaim(EXPIRES_AT.getJoseName(), bodyClaims.get("exp"));
        // claims.setClaim(ALGORITHM.getJoseName(), signatureType.equals(URN_BSI_TR_03111_ECDSA) ? "BP256R1" : "PS256");
        // claims.setClaim(CONTENT_TYPE.getJoseName(), "NJWT");

        // String signedChallenge = getSignedJwt(
        //     smcbAuthCert,
        //     claims,
        //     signatureType,
        //     smcbHandle,
        //     true,
        //     idpFunc
        // );

        String signedChallenge = signCodeChallenge(smcbAuthCert, challengeToken, signatureType, smcbHandle);
        String challenge = preparePostSignedCodeChallenge(authChallenge, signedChallenge);

        // todo verifyPin

        AuthenticationRequest request = AuthenticationRequest.builder()
            .authenticationEndpointUrl(discoveryDocumentResponse.getAuthorizationEndpoint())
            .signedChallenge(new IdpJwe(challenge))
            .build();
        AuthenticationResponse authenticationResponse = authenticatorClient.performAuthentication(
            request, UnaryOperator.identity(), o -> {}
        );

        String codeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(DigestUtils.sha256(codeChallenge));

        TokenRequest tokenRequest = TokenRequest.builder()
            .tokenUrl(discoveryDocumentResponse.getTokenEndpoint())
            .clientId(idpClientId)
            .code(authenticationResponse.getCode())
            .ssoToken(authenticationResponse.getSsoToken())
            .redirectUrl(idpAuthRequestRedirectUrl)
            .codeVerifier(codeVerifier)
            .idpEnc(discoveryDocumentResponse.getIdpEnc())
            .build();
        IdpTokenResult idpTokenResult = authenticatorClient.retrieveAccessToken(tokenRequest, UnaryOperator.identity(), o -> {});

        tokenConsumer.accept(idpTokenResult.getAccessToken().getRawString());
    }

    private String preparePostSignedCodeChallenge(AuthenticationChallenge authChallenge, String jws) throws Exception {
        Map<String, Object> bodyClaims = authChallenge.getChallenge().extractBodyClaims();
        Long expChallengeToken = (Long) bodyClaims.get("exp");

        ObjectMapper M = new ObjectMapper();
        ObjectNode jwePayload = M.createObjectNode();
        jwePayload.put("njwt", jws);
        String jwePayloadJson = M.writeValueAsString(jwePayload);

        JsonWebEncryption jwe = new JsonWebEncryption();
        jwe.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.ECDH_ES);
        jwe.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_256_GCM);
        jwe.setKey(discoveryDocumentResponse.getIdpEnc());
        jwe.setContentTypeHeaderValue("NJWT");
        jwe.getHeaders().setObjectHeaderValue("exp", expChallengeToken);
        jwe.setPayload(jwePayloadJson);

        String compactJwe = jwe.getCompactSerialization();

        return URLEncoder.encode(compactJwe, StandardCharsets.UTF_8);
    }
}