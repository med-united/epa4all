package de.servicehealth.epa4all.server.idp;

import de.gematik.idp.client.data.AuthorizationRequest;
import de.gematik.idp.field.CodeChallengeMethod;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.RandomStringUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static de.gematik.idp.field.CodeChallengeMethod.S256;
import static de.servicehealth.epa4all.server.idp.utils.IdpUtils.generateCodeVerifier;
import static org.apache.commons.codec.digest.DigestUtils.sha256;

@AllArgsConstructor
public class AuthorizationData {

    private String codeChallenge;
    private String clientId;
    @Getter
    private CodeChallengeMethod codeChallengeMethod;
    private String redirectUri;
    private String state;
    private Set<String> scopes;
    private String nonce;

    public static AuthorizationData fromLocation(URI location) {
        Map<String, String> queryMap = new HashMap<>();
        Arrays.stream(location.getQuery().split("&")).map(s -> s.split("=")).forEach(s -> queryMap.put(s[0], s[1]));

        String codeChallenge = queryMap.get("code_challenge");
        String clientId = queryMap.get("client_id");
        CodeChallengeMethod codeChallengeMethod = CodeChallengeMethod.valueOf(queryMap.get("code_challenge_method"));
        String redirectUri = queryMap.get("redirect_uri");
        String state = queryMap.get("state");
        Set<String> scopes = Set.of(queryMap.get("scope").replace("+", " "));
        String nonce = queryMap.get("nonce");

        return new AuthorizationData(codeChallenge, clientId, codeChallengeMethod, redirectUri, state, scopes, nonce);
    }

    @SuppressWarnings("deprecation")
    public static AuthorizationData fromConfig(IdpConfig idpConfig, Set<String> scopes) {
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = new String(Base64.getUrlEncoder().withoutPadding().encode(sha256(codeVerifier)));
        String clientId = idpConfig.getClientId();
        String redirectUrl = idpConfig.getAuthRequestRedirectUrl();
        String state = RandomStringUtils.randomAlphanumeric(20);
        String nonce = RandomStringUtils.randomAlphanumeric(20);
        return new AuthorizationData(codeChallenge, clientId, S256, redirectUrl, state, scopes, nonce);
    }

    public AuthorizationRequest buildAuthorizationRequest(String authorizationEndpoint) {
        return AuthorizationRequest.builder()
            .link(authorizationEndpoint)
            .clientId(clientId)
            .codeChallenge(codeChallenge)
            .codeChallengeMethod(codeChallengeMethod)
            .redirectUri(redirectUri)
            .state(state)
            .scopes(scopes)
            .nonce(nonce)
            .build();
    }
}