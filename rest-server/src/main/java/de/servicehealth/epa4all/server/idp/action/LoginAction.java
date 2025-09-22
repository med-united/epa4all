package de.servicehealth.epa4all.server.idp.action;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.IdpTokenResult;
import de.gematik.idp.client.data.AuthenticationResponse;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.gematik.idp.client.data.TokenRequest;
import de.health.service.cetp.CertificateInfo;
import de.servicehealth.epa4all.server.idp.func.IdpFunc;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class LoginAction extends AbstractAuthAction {

    private final String idpClientId;
    private final String idpAuthRequestRedirectUrl;
    private final Consumer<String> tokenConsumer;

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
    }

    @Override
    public void execute(
        String epaNonce,
        String smcbHandle,
        CertificateInfo certificateInfo,
        AuthenticationChallenge authChallenge
    ) throws NoSuchAlgorithmException, IOException {
        AuthenticationResponse authenticationResponse = processAuthenticationChallenge(
            smcbHandle, certificateInfo, authChallenge
        );
        String codeChallenge = (String) authChallenge.getChallenge().extractBodyClaims().get("code_challenge");
        TokenRequest tokenRequest = TokenRequest.builder()
            .tokenUrl(discoveryDocumentResponse.getTokenEndpoint())
            .clientId(idpClientId)
            .code(authenticationResponse.getCode())
            .ssoToken(authenticationResponse.getSsoToken())
            .redirectUrl(idpAuthRequestRedirectUrl)
            .codeVerifier(codeChallenge)
            .idpEnc(discoveryDocumentResponse.getIdpEnc())
            .build();
        IdpTokenResult idpTokenResult = authenticatorClient.retrieveAccessToken(tokenRequest, UnaryOperator.identity(), o -> {
        });
        tokenConsumer.accept(idpTokenResult.getAccessToken().getRawString());
    }
}