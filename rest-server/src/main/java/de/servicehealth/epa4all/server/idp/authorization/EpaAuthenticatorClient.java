package de.servicehealth.epa4all.server.idp.authorization;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.IdpClientRuntimeException;
import de.gematik.idp.client.IdpTokenResult;
import de.gematik.idp.client.data.AuthenticationRequest;
import de.gematik.idp.client.data.AuthenticationResponse;
import de.gematik.idp.client.data.AuthorizationRequest;
import de.gematik.idp.client.data.AuthorizationResponse;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.gematik.idp.client.data.TokenRequest;
import de.gematik.idp.crypto.EcKeyUtility;
import de.gematik.idp.crypto.Nonce;
import de.gematik.idp.data.IdpErrorResponse;
import de.gematik.idp.error.IdpErrorType;
import de.gematik.idp.token.IdpJwe;
import de.gematik.idp.token.JsonWebToken;
import jakarta.ws.rs.core.HttpHeaders;
import kong.unirest.core.GetRequest;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.JsonNode;
import kong.unirest.core.MultipartBody;
import kong.unirest.core.UnirestInstance;
import kong.unirest.core.json.JSONObject;
import org.jose4j.jwt.JwtClaims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
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

import static de.gematik.idp.authentication.UriUtils.extractParameterValue;
import static de.gematik.idp.authentication.UriUtils.extractParameterValueOptional;
import static de.gematik.idp.crypto.CryptoLoader.getCertificateFromPem;
import static de.gematik.idp.field.ClaimName.AUTHORIZATION_ENDPOINT;
import static de.gematik.idp.field.ClaimName.AUTH_PAIR_ENDPOINT;
import static de.gematik.idp.field.ClaimName.CLIENT_ID;
import static de.gematik.idp.field.ClaimName.CODE_CHALLENGE;
import static de.gematik.idp.field.ClaimName.CODE_CHALLENGE_METHOD;
import static de.gematik.idp.field.ClaimName.CODE_VERIFIER;
import static de.gematik.idp.field.ClaimName.REDIRECT_URI;
import static de.gematik.idp.field.ClaimName.RESPONSE_TYPE;
import static de.gematik.idp.field.ClaimName.SCOPE;
import static de.gematik.idp.field.ClaimName.SSO_ENDPOINT;
import static de.gematik.idp.field.ClaimName.STATE;
import static de.gematik.idp.field.ClaimName.TOKEN_ENDPOINT;
import static de.gematik.idp.field.ClaimName.TOKEN_KEY;
import static de.gematik.idp.field.ClaimName.URI_PAIR;
import static de.gematik.idp.field.ClaimName.URI_PUK_IDP_ENC;
import static de.gematik.idp.field.ClaimName.URI_PUK_IDP_SIG;
import static de.gematik.idp.field.ClaimName.X509_CERTIFICATE_CHAIN;
import static jakarta.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class EpaAuthenticatorClient extends AuthenticatorClient {

    private static final Logger log = LoggerFactory.getLogger(EpaAuthenticatorClient.class.getName());

    private final String userAgent;

    public EpaAuthenticatorClient(UnirestInstance unirestInstance, String userAgent) {
        super(unirestInstance);

        this.userAgent = userAgent;
    }

    public DiscoveryDocumentResponse retrieveDiscoveryDocument(
        final String discoveryDocumentUrl, final Optional<String> fixedIdpHost) {
        final HttpResponse<String> discoveryDocumentResponse =
            getUnirestInstance()
                .get(patchIdpHost(discoveryDocumentUrl, fixedIdpHost))
                .header(HttpHeaders.USER_AGENT, userAgent)
                .asString();
        final JsonWebToken discoveryDocument = new JsonWebToken(discoveryDocumentResponse.getBody());

        final Supplier<IdpClientRuntimeException> exceptionSupplier =
            () -> new IdpClientRuntimeException("Incomplete Discovery Document encountered!");
        return DiscoveryDocumentResponse.builder()
            .authorizationEndpoint(
                patchIdpHost(
                    discoveryDocument
                        .getStringBodyClaim(AUTHORIZATION_ENDPOINT)
                        .orElseThrow(exceptionSupplier),
                    fixedIdpHost))
            .tokenEndpoint(
                patchIdpHost(
                    discoveryDocument.getStringBodyClaim(TOKEN_ENDPOINT).orElseThrow(exceptionSupplier),
                    fixedIdpHost))
            .ssoEndpoint(
                patchIdpHost(
                    discoveryDocument.getStringBodyClaim(SSO_ENDPOINT).orElseThrow(exceptionSupplier),
                    fixedIdpHost))
            .discSig(discoveryDocument.getClientCertificateFromHeader().orElseThrow(exceptionSupplier))
            .pairingEndpoint(
                discoveryDocument
                    .getStringBodyClaim(URI_PAIR)
                    .map(uriPair -> patchIdpHost(uriPair, fixedIdpHost))
                    .orElse("<IDP DOES NOT SUPPORT ALTERNATIVE AUTHENTICATION>"))
            .authPairEndpoint(
                discoveryDocument
                    .getStringBodyClaim(AUTH_PAIR_ENDPOINT)
                    .map(uriPair -> patchIdpHost(uriPair, fixedIdpHost))
                    .orElse("<IDP DOES NOT SUPPORT ALTERNATIVE AUTHENTICATION>"))
            .idpSig(
                retrieveServerCertFromLocation(
                    patchIdpHost(
                        discoveryDocument
                            .getStringBodyClaim(URI_PUK_IDP_SIG)
                            .orElseThrow(exceptionSupplier),
                        fixedIdpHost)))
            .idpEnc(
                retrieveServerPuKFromLocation(
                    patchIdpHost(
                        discoveryDocument
                            .getStringBodyClaim(URI_PUK_IDP_ENC)
                            .orElseThrow(exceptionSupplier),
                        fixedIdpHost)))
            .build();
    }

    private String patchIdpHost(final String unpatchedUrl, final Optional<String> fixedIdpHost) {
        if (fixedIdpHost.isEmpty()) {
            return unpatchedUrl;
        }

        try {
            final URI newHostUri = new URI(fixedIdpHost.get());
            final URI unpatchedUri = new URI(unpatchedUrl);
            if (!fixedIdpHost.get().contains("://")) {
                final String patchedUri =
                    unpatchedUri.getScheme() + "://" + newHostUri + unpatchedUri.getRawPath();
                log.info(
                    "Patching URL. Original: {}, Patch: {}, Result: {}",
                    unpatchedUrl,
                    fixedIdpHost.get(),
                    patchedUri);
                return patchedUri;
            } else {
                final String patchedUri =
                    Optional.ofNullable(newHostUri.getScheme()).orElse(unpatchedUri.getScheme())
                        + "://"
                        + Optional.ofNullable(newHostUri.getRawAuthority())
                        .orElse(unpatchedUri.getRawAuthority())
                        + Optional.ofNullable(newHostUri.getPath()).orElse("")
                        + unpatchedUri.getPath();

                log.info(
                    "Patching URL. Original: {}, Patch: {}, Result: {}",
                    unpatchedUrl,
                    fixedIdpHost.get(),
                    patchedUri);
                return patchedUri;
            }
        } catch (final Exception e) {
            throw new IdpClientRuntimeException(
                "Error while patching with template '" + fixedIdpHost.get() + "'", e);
        }
    }

    public AuthorizationResponse doAuthorizationRequest(
        final AuthorizationRequest authorizationRequest,
        final UnaryOperator<GetRequest> beforeCallback,
        final Consumer<HttpResponse<AuthenticationChallenge>> afterCallback) {
        final String scope = String.join(" ", authorizationRequest.getScopes());

        final GetRequest request =
            getUnirestInstance()
                .get(authorizationRequest.getLink())
                .queryString(CLIENT_ID.getJoseName(), authorizationRequest.getClientId())
                .queryString(RESPONSE_TYPE.getJoseName(), "code")
                .queryString(REDIRECT_URI.getJoseName(), authorizationRequest.getRedirectUri())
                .queryString(STATE.getJoseName(), authorizationRequest.getState())
                .queryString(CODE_CHALLENGE.getJoseName(), authorizationRequest.getCodeChallenge())
                .queryString(
                    CODE_CHALLENGE_METHOD.getJoseName(), authorizationRequest.getCodeChallengeMethod())
                .queryString(SCOPE.getJoseName(), scope)
                .queryString("nonce", authorizationRequest.getNonce())
                .header(HttpHeaders.USER_AGENT, userAgent)
                .header(HttpHeaders.ACCEPT, APPLICATION_JSON);

        final HttpResponse<AuthenticationChallenge> authorizationResponse =
            beforeCallback.apply(request).asObject(AuthenticationChallenge.class);
        afterCallback.accept(authorizationResponse);
        checkResponseForErrorsAndThrowIfAny(authorizationResponse);
        return AuthorizationResponse.builder()
            .authenticationChallenge(authorizationResponse.getBody())
            .build();
    }

    public IdpTokenResult retrieveAccessToken(
        final TokenRequest tokenRequest,
        final UnaryOperator<MultipartBody> beforeTokenCallback,
        final Consumer<HttpResponse<JsonNode>> afterTokenCallback) {
        final byte[] tokenKeyBytes = Nonce.randomBytes(256 / 8);
        final SecretKey tokenKey = new SecretKeySpec(tokenKeyBytes, "AES");
        final IdpJwe keyVerifierToken =
            buildKeyVerifierToken(
                tokenKeyBytes, tokenRequest.getCodeVerifier(), tokenRequest.getIdpEnc());

        final MultipartBody request =
            getUnirestInstance()
                .post(tokenRequest.getTokenUrl())
                .field("grant_type", "authorization_code")
                .field("client_id", tokenRequest.getClientId())
                .field("code", tokenRequest.getCode())
                .field("key_verifier", keyVerifierToken.getRawString())
                .field("redirect_uri", tokenRequest.getRedirectUrl())
                .contentType(APPLICATION_FORM_URLENCODED)
                .header(HttpHeaders.USER_AGENT, userAgent)
                .header(HttpHeaders.ACCEPT, APPLICATION_JSON);

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
            .idToken(decryptToken(tokenKey, jsonObject.get("id_token")))
            .ssoToken(
                tokenRequest.getSsoToken() == null ? null : new IdpJwe(tokenRequest.getSsoToken()))
            .build();
    }

    private IdpJwe buildKeyVerifierToken(
        final byte[] tokenKeyBytes, final String codeVerifier, final PublicKey idpEnc) {
        final JwtClaims claims = new JwtClaims();
        claims.setStringClaim(
            TOKEN_KEY.getJoseName(),
            new String(Base64.getUrlEncoder().withoutPadding().encode(tokenKeyBytes)));
        claims.setStringClaim(CODE_VERIFIER.getJoseName(), codeVerifier);

        return IdpJwe.createWithPayloadAndEncryptWithKey(claims.toJson(), idpEnc, "JSON");
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

    public AuthenticationResponse performAuthentication(
        final AuthenticationRequest authenticationRequest,
        final UnaryOperator<MultipartBody> beforeAuthenticationCallback,
        final Consumer<HttpResponse<String>> afterAuthenticationCallback) {

        final MultipartBody request =
            getUnirestInstance()
                .post(authenticationRequest.getAuthenticationEndpointUrl())
                .field("signed_challenge", authenticationRequest.getSignedChallenge().getRawString())
                .contentType(APPLICATION_FORM_URLENCODED)
                .header(HttpHeaders.USER_AGENT, userAgent);

        final HttpResponse<String> loginResponse =
            beforeAuthenticationCallback.apply(request).asString();
        afterAuthenticationCallback.accept(loginResponse);
        checkResponseForErrorsAndThrowIfAny(loginResponse);
        final String location = retrieveLocationFromResponse(loginResponse);

        return AuthenticationResponse.builder()
            .code(extractParameterValue(location, "code"))
            .location(location)
            .ssoToken(extractParameterValueOptional(location, "ssotoken").orElse(null))
            .build();
    }

    private String retrieveLocationFromResponse(final HttpResponse<String> response) {
        if (response.getStatus() != 302) {
            throw new IdpClientRuntimeException(
                "Unexpected status code in response: " + response.getStatus());
        }
        return response.getHeaders().getFirst("Location");
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

    X509Certificate retrieveServerCertFromLocation(final String uri) {
        final HttpResponse<JsonNode> pukAuthResponse =
            getUnirestInstance().get(uri).header(HttpHeaders.USER_AGENT, userAgent).asJson();
        final JSONObject keyObject = pukAuthResponse.getBody().getObject();
        final String verificationCertificate =
            keyObject.getJSONArray(X509_CERTIFICATE_CHAIN.getJoseName()).getString(0);
        return getCertificateFromPem(Base64.getDecoder().decode(verificationCertificate));
    }

    PublicKey retrieveServerPuKFromLocation(final String uri) {
        final HttpResponse<JsonNode> pukAuthResponse =
            getUnirestInstance().get(uri).header(HttpHeaders.USER_AGENT, userAgent).asJson();
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
