package de.servicehealth.epa4all.server.idp.authorization;

import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.IdpClientRuntimeException;
import de.gematik.idp.client.IdpTokenResult;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.gematik.idp.client.data.TokenRequest;
import de.gematik.idp.crypto.EcKeyUtility;
import de.gematik.idp.crypto.Nonce;
import de.gematik.idp.data.IdpErrorResponse;
import de.gematik.idp.error.IdpErrorType;
import de.gematik.idp.token.IdpJwe;
import de.gematik.idp.token.JsonWebToken;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.JsonNode;
import kong.unirest.core.MultipartBody;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestInstance;
import kong.unirest.core.json.JSONObject;
import org.jose4j.jwt.JwtClaims;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static de.gematik.idp.authentication.UriUtils.extractParameterValueOptional;
import static de.gematik.idp.crypto.CryptoLoader.getCertificateFromPem;
import static de.gematik.idp.field.ClaimName.AUTHORIZATION_ENDPOINT;
import static de.gematik.idp.field.ClaimName.AUTH_PAIR_ENDPOINT;
import static de.gematik.idp.field.ClaimName.CODE_VERIFIER;
import static de.gematik.idp.field.ClaimName.TOKEN_ENDPOINT;
import static de.gematik.idp.field.ClaimName.TOKEN_KEY;
import static de.gematik.idp.field.ClaimName.URI_PAIR;
import static de.gematik.idp.field.ClaimName.URI_PUK_IDP_ENC;
import static de.gematik.idp.field.ClaimName.URI_PUK_IDP_SIG;
import static de.gematik.idp.field.ClaimName.X509_CERTIFICATE_CHAIN;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.USER_AGENT;
import static jakarta.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class TSSAuthenticatorClient extends AuthenticatorClient {

    private static final String UserAgent = "IdP-Client";

    private final UnirestInstance unirestInstance;

    public TSSAuthenticatorClient(UnirestInstance unirestInstance) {
        super(unirestInstance);
        this.unirestInstance = unirestInstance;
    }

    @Override
    public IdpTokenResult retrieveAccessToken(
        final TokenRequest tokenRequest,
        final UnaryOperator<MultipartBody> beforeTokenCallback,
        final Consumer<HttpResponse<JsonNode>> afterTokenCallback
    ) {
        final byte[] tokenKeyBytes = Nonce.randomBytes(256 / 8);
        final SecretKey tokenKey = new SecretKeySpec(tokenKeyBytes, "AES");
        final IdpJwe keyVerifierToken = buildKeyVerifierToken(
            tokenKeyBytes, tokenRequest.getCodeVerifier(), tokenRequest.getIdpEnc()
        );

        final MultipartBody request =
            unirestInstance
                .post(tokenRequest.getTokenUrl())
                .field("grant_type", "authorization_code")
                .field("client_id", tokenRequest.getClientId())
                .field("code", tokenRequest.getCode())
                .field("key_verifier", keyVerifierToken.getRawString())
                .field("redirect_uri", tokenRequest.getRedirectUrl())
                .contentType(APPLICATION_FORM_URLENCODED)
                .header(USER_AGENT, UserAgent)
                .header(ACCEPT, APPLICATION_JSON);

        final HttpResponse<JsonNode> tokenResponse = beforeTokenCallback.apply(request).asJson();
        afterTokenCallback.accept(tokenResponse);
        checkResponseForErrorsAndThrowIfAny(tokenResponse);
        final JSONObject jsonObject = tokenResponse.getBody().getObject();

        final String tokenType = tokenResponse.getBody().getObject().getString("token_type");
        final int expiresIn = tokenResponse.getBody().getObject().getInt("expires_in");

        return IdpTokenResult.builder()
            .tokenType(tokenType)
            .expiresIn(expiresIn)
            .accessToken(decryptToken(tokenKey, jsonObject.get("access_token")))
            .ssoToken(tokenRequest.getSsoToken() == null ? null : new IdpJwe(tokenRequest.getSsoToken()))
            .build();
    }

    private JsonWebToken decryptToken(final SecretKey tokenKey, final Object tokenValue) {
        return Optional.ofNullable(tokenValue)
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(IdpJwe::new)
            .map(jwe -> jwe.decryptNestedJwt(tokenKey))
            .orElseThrow(
                () -> new IdpClientRuntimeException("Unable to extract Access-Token from response!"));
    }

    private IdpJwe buildKeyVerifierToken(final byte[] tokenKeyBytes, final String codeVerifier, final PublicKey idpEnc) {
        final JwtClaims claims = new JwtClaims();
        claims.setStringClaim(
            TOKEN_KEY.getJoseName(),
            new String(Base64.getUrlEncoder().withoutPadding().encode(tokenKeyBytes)));
        claims.setStringClaim(CODE_VERIFIER.getJoseName(), codeVerifier);

        return IdpJwe.createWithPayloadAndEncryptWithKey(claims.toJson(), idpEnc, "JSON");
    }

    private void checkResponseForErrorsAndThrowIfAny(final HttpResponse<?> loginResponse) {
        if (loginResponse.getStatus() == 302) {
            checkForForwardingExceptionAndThrowIfPresent(loginResponse.getHeaders().getFirst("Location"));
        }
        if (loginResponse.getStatus() / 100 == 4) {
            IdpErrorResponse response = new IdpErrorResponse();
            try {
                response = loginResponse.mapError(IdpErrorResponse.class);
            } catch (final Exception e) {
                // swallow
            }
            throw new IdpClientRuntimeException(
                "Unexpected Server-Response "
                    + loginResponse.getStatus()
                    + " "
                    + loginResponse.mapError(String.class),
                Optional.ofNullable(response.getCode()),
                Optional.ofNullable(response.getError()));
        }
    }

    private void checkForForwardingExceptionAndThrowIfPresent(final String location) {
        extractParameterValueOptional(location, "error")
            .ifPresent(
                errorCode -> {
                    Optional<String> gematikCode = Optional.empty();
                    Optional<IdpErrorType> errorDescription = Optional.empty();
                    try {
                        gematikCode = extractParameterValueOptional(location, "gematik_code");
                        errorDescription =
                            extractParameterValueOptional(location, "error_description")
                                .flatMap(IdpErrorType::fromSerializationValue);
                    } catch (final Exception e) {
                        // swallow
                    }
                    throw new IdpClientRuntimeException(
                        "Server-Error with message: "
                            + extractParameterValueOptional(location, "gematik_code")
                            .map(code -> code + ": ")
                            .orElse("")
                            + extractParameterValueOptional(location, "error_description")
                            .orElse(errorCode),
                        gematikCode,
                        errorDescription);
                });
    }

    @Override
    public DiscoveryDocumentResponse retrieveDiscoveryDocument(
        final String discoveryDocumentUrl, final Optional<String> fixedIdpHost
    ) {
        final HttpResponse<String> discoveryDocumentResponse =
            Unirest.get(discoveryDocumentUrl)
                .header(USER_AGENT, UserAgent)
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
            Unirest.get(uri).header(USER_AGENT, UserAgent).asJson();
        final JSONObject keyObject = pukAuthResponse.getBody().getObject();
        final String verificationCertificate =
            keyObject.getJSONArray(X509_CERTIFICATE_CHAIN.getJoseName()).getString(0);
        return getCertificateFromPem(Base64.getDecoder().decode(verificationCertificate));
    }

    private PublicKey retrieveServerPuKFromLocation(final String uri) {
        final HttpResponse<JsonNode> pukAuthResponse =
            Unirest.get(uri).header(USER_AGENT, UserAgent).asJson();
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