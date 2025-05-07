package de.servicehealth.epa4all.server.idp.utils;

import de.gematik.ws.conn.signatureservice.v7.BinaryDocumentType;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticate;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticateResponse;
import de.servicehealth.epa4all.server.idp.func.IdpFunc;
import oasis.names.tc.dss._1_0.core.schema.Base64Data;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

import static de.gematik.idp.brainPoolExtension.BrainpoolAlgorithmSuiteIdentifiers.BRAINPOOL256_USING_SHA256;
import static de.health.service.cetp.CertificateInfo.URN_BSI_TR_03111_ECDSA;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jose4j.jws.AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256;
import static org.jose4j.jws.AlgorithmIdentifiers.RSA_PSS_USING_SHA256;
import static org.jose4j.jws.EcdsaUsingShaAlgorithm.convertDerToConcatenated;

public class IdpUtils {

    public static JsonWebSignature getJsonWebSignature(X509Certificate certificate, boolean nonce, String payload) {
        final JsonWebSignature jsonWebSignature = new JsonWebSignature();
        jsonWebSignature.setPayload(payload);
        jsonWebSignature.setHeader("typ", "JWT");
        jsonWebSignature.setCertificateChainHeaderValue(certificate);
        boolean ec = certificate.getPublicKey() instanceof ECPublicKey;
        if (!nonce) {
            jsonWebSignature.setHeader("cty", "NJWT");
            jsonWebSignature.setAlgorithmHeaderValue(ec ? BRAINPOOL256_USING_SHA256 : RSA_PSS_USING_SHA256);
        } else {
            jsonWebSignature.setAlgorithmHeaderValue(ec ? ECDSA_USING_P256_CURVE_AND_SHA256 : RSA_PSS_USING_SHA256);
        }
        return jsonWebSignature;
    }

    public static String getSignedJwt(
        X509Certificate certificate,
        final JwtClaims claims,
        String signatureType,
        String smcbHandle,
        boolean nonce,
        IdpFunc idpFunc
    ) {
        String payload = claims.toJson();
        final JsonWebSignature jsonWebSignature = getJsonWebSignature(certificate, nonce, payload);
        String body = jsonWebSignature.getHeaders().getEncodedHeader() + "." + jsonWebSignature.getEncodedPayload();
        byte[] challengeBytes = body.getBytes(UTF_8);
        // A_24751 - Challenge signieren als ECDSA-Signatur
        // A_24752 - Challenge signieren als PKCS#1-Signatur
        byte[] signatureBytes = hashAndSignBytesWithExternalAuthenticateWithSMCB(challengeBytes, signatureType, smcbHandle, idpFunc);
        return body + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
    }

    private static byte[] hashAndSignBytesWithExternalAuthenticateWithSMCB(
        byte[] inputBytes,
        String signatureType,
        String smcbHandle,
        IdpFunc idpFunc
    ) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Could not apply SHA-256 to signing bytes", e);
        }
        byte[] encodedHash = digest.digest(inputBytes);

        ExternalAuthenticate externalAuthenticate = new ExternalAuthenticate();
        BinaryDocumentType binaryDocumentType = new BinaryDocumentType();
        Base64Data base64Data = new Base64Data();
        base64Data.setValue(encodedHash);
        base64Data.setMimeType("application/octet-stream");
        binaryDocumentType.setBase64Data(base64Data);
        externalAuthenticate.setBinaryString(binaryDocumentType);
        externalAuthenticate.setContext(idpFunc.getCtxSupplier().get());
        externalAuthenticate.setCardHandle(smcbHandle);
        ExternalAuthenticate.OptionalInputs optionalInputs = new ExternalAuthenticate.OptionalInputs();
        // A_24883-02 - clientAttest als ECDSA-Signatur
        optionalInputs.setSignatureType(signatureType);
        externalAuthenticate.setOptionalInputs(optionalInputs);

        ExternalAuthenticateResponse externalAuthenticateResponse = idpFunc.getExtAuthFunc().apply(externalAuthenticate);
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
}