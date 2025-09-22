package de.servicehealth.epa4all.server.idp.authorization;

import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.IdpClientRuntimeException;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.gematik.idp.crypto.EcKeyUtility;
import de.gematik.idp.token.JsonWebToken;
import jakarta.ws.rs.core.HttpHeaders;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.JsonNode;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestInstance;
import kong.unirest.core.json.JSONObject;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Supplier;

import static de.gematik.idp.crypto.CryptoLoader.getCertificateFromPem;
import static de.gematik.idp.field.ClaimName.AUTHORIZATION_ENDPOINT;
import static de.gematik.idp.field.ClaimName.AUTH_PAIR_ENDPOINT;
import static de.gematik.idp.field.ClaimName.TOKEN_ENDPOINT;
import static de.gematik.idp.field.ClaimName.URI_PAIR;
import static de.gematik.idp.field.ClaimName.URI_PUK_IDP_ENC;
import static de.gematik.idp.field.ClaimName.URI_PUK_IDP_SIG;
import static de.gematik.idp.field.ClaimName.X509_CERTIFICATE_CHAIN;
import static jakarta.ws.rs.core.HttpHeaders.USER_AGENT;

public class TSSAuthenticatorClient extends AuthenticatorClient {

    private static final String USER_AGENT = "IdP-Client";

    public TSSAuthenticatorClient(UnirestInstance unirestInstance) {
        super(unirestInstance);
    }

    @Override
    public DiscoveryDocumentResponse retrieveDiscoveryDocument(
        final String discoveryDocumentUrl, final Optional<String> fixedIdpHost
    ) {
        final HttpResponse<String> discoveryDocumentResponse =
            Unirest.get(discoveryDocumentUrl)
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .asString();
        final JsonWebToken discoveryDocument = new JsonWebToken(discoveryDocumentResponse.getBody());

        final Supplier<IdpClientRuntimeException> exceptionSupplier =
            () -> new IdpClientRuntimeException("Incomplete Discovery Document encountered!");
        return DiscoveryDocumentResponse.builder()
            .authorizationEndpoint(
                discoveryDocument
                    .getStringBodyClaim(AUTHORIZATION_ENDPOINT)
                    .orElseThrow(exceptionSupplier))
            .tokenEndpoint(discoveryDocument.getStringBodyClaim(TOKEN_ENDPOINT).orElseThrow(exceptionSupplier))
            .discSig(
                discoveryDocument.getClientCertificateFromHeader().orElseThrow(exceptionSupplier)
            )
            .pairingEndpoint(
                discoveryDocument
                    .getStringBodyClaim(URI_PAIR)
                    .orElse("<IDP DOES NOT SUPPORT ALTERNATIVE AUTHENTICATION>"))
            .authPairEndpoint(
                discoveryDocument
                    .getStringBodyClaim(AUTH_PAIR_ENDPOINT)
                    .orElse("<IDP DOES NOT SUPPORT ALTERNATIVE AUTHENTICATION>"))
            .idpSig(
                retrieveServerCertFromLocation(
                    discoveryDocument
                        .getStringBodyClaim(URI_PUK_IDP_SIG)
                        .orElseThrow(exceptionSupplier)))
            .idpEnc(
                retrieveServerPuKFromLocation(
                    discoveryDocument
                        .getStringBodyClaim(URI_PUK_IDP_ENC)
                        .orElseThrow(exceptionSupplier)))
            .build();
    }

    private X509Certificate retrieveServerCertFromLocation(final String uri) {
        final HttpResponse<JsonNode> pukAuthResponse =
            Unirest.get(uri).header(HttpHeaders.USER_AGENT, USER_AGENT).asJson();
        final JSONObject keyObject = pukAuthResponse.getBody().getObject();
        final String verificationCertificate =
            keyObject.getJSONArray(X509_CERTIFICATE_CHAIN.getJoseName()).getString(0);
        return getCertificateFromPem(Base64.getDecoder().decode(verificationCertificate));
    }

    private PublicKey retrieveServerPuKFromLocation(final String uri) {
        final HttpResponse<JsonNode> pukAuthResponse =
            Unirest.get(uri).header(HttpHeaders.USER_AGENT, USER_AGENT).asJson();
        final JSONObject keyObject = pukAuthResponse.getBody().getObject();
        try {
            return getPublicKey(keyObject);
        } catch (final InvalidKeySpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IdpClientRuntimeException(
                "Unable to construct public key from given uri '" + uri + "', got " + e.getMessage());
        }
    }

    private static PublicKey getPublicKey(final JSONObject keyObject)
        throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException {
        return EcKeyUtility.genECPublicKey(
            "brainpoolP256r1", keyObject.getString("x"), keyObject.getString("y"));
    }
}
