package de.servicehealth.epa4all.server.idp.utils;

import de.gematik.ws.conn.signatureservice.v7.BinaryDocumentType;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticate;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticateResponse;
import de.servicehealth.epa4all.server.idp.func.IdpFunc;
import oasis.names.tc.dss._1_0.core.schema.Base64Data;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.function.UnaryOperator;

import static de.gematik.idp.brainPoolExtension.BrainpoolAlgorithmSuiteIdentifiers.BRAINPOOL256_USING_SHA256;
import static de.health.service.cetp.CertificateInfo.URN_BSI_TR_03111_ECDSA;
import static org.jose4j.jws.AlgorithmIdentifiers.RSA_PSS_USING_SHA256;
import static org.jose4j.jws.EcdsaUsingShaAlgorithm.convertDerToConcatenated;

public class IdpUtils {

    public static String getSignedJwt(
        X509Certificate certificate,
        final JwtClaims claims,
        String signatureType,
        String smcbHandle,
        boolean nonce,
        IdpFunc idpFunc
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
                            challengeBytes, signatureType, smcbHandle, idpFunc
                        );
                    },
                    jsonWebSignature,
                    sigData -> sigData));

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
        byte[] encodedhash = digest.digest(inputBytes);

        ExternalAuthenticate externalAuthenticate = new ExternalAuthenticate();
        BinaryDocumentType binaryDocumentType = new BinaryDocumentType();
        Base64Data base64Data = new Base64Data();
        base64Data.setValue(encodedhash);
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

    private static byte[] getSignatureBytes(
        final UnaryOperator<byte[]> contentSigner,
        final JsonWebSignature jsonWebSignature,
        final UnaryOperator<byte[]> signatureStripper
    ) {
        String encoded = jsonWebSignature.getHeaders().getEncodedHeader() + "." + jsonWebSignature.getEncodedPayload();
        byte[] bytes = encoded.getBytes(StandardCharsets.UTF_8);
        return signatureStripper.apply(contentSigner.apply(bytes));
    }
}