package de.servicehealth.epa4all.idp.action;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.IdpTokenResult;
import de.gematik.idp.client.data.AuthenticationResponse;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.gematik.idp.client.data.TokenRequest;
import de.servicehealth.epa4all.idp.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.serviceport.IServicePortAggregator;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;

import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class LoginAction extends AbstractAuthAction {

    private final String idpClientId;
    private final String idpAuthRequestRedirectUrl;
    private final Consumer<String> authConsumer;

    public LoginAction(
        String idpClientId,
        String idpAuthRequestRedirectUrl,
        IServicePortAggregator servicePorts,
        AuthenticatorClient authenticatorClient,
        AuthorizationSmcBApi authorizationService,
        DiscoveryDocumentResponse discoveryDocumentResponse,
        Consumer<String> authConsumer
    ) {
        super(servicePorts, authenticatorClient, authorizationService, discoveryDocumentResponse);

        this.idpClientId = idpClientId;
        this.idpAuthRequestRedirectUrl = idpAuthRequestRedirectUrl;
        this.authConsumer = authConsumer;
    }

    @Override
    public void execute(
        AuthenticationChallenge authChallenge,
        X509Certificate smcbAuthCert,
        String smcbHandle,
        String clientAttest,
        String signatureType
    ) {
        AuthenticationResponse authenticationResponse = processAuthenticationChallenge(
            smcbHandle, authChallenge, smcbAuthCert, signatureType
        );

        String codeVerifier = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(DigestUtils.sha256(authChallenge.getChallenge().getRawString()));

        TokenRequest tokenRequest = TokenRequest.builder()
            .tokenUrl(discoveryDocumentResponse.getTokenEndpoint())
            .clientId(idpClientId)
            .code(authenticationResponse.getCode())
            .ssoToken(authenticationResponse.getSsoToken())
            .redirectUrl(idpAuthRequestRedirectUrl)
            .codeVerifier(codeVerifier)
            .idpEnc(discoveryDocumentResponse.getIdpEnc())
            .build();

        IdpTokenResult idpTokenResult = authenticatorClient.retrieveAccessToken(
            tokenRequest, UnaryOperator.identity(), o -> {
            }
        );
        LocalDateTime validUntil = idpTokenResult.getValidUntil();
        authConsumer.accept(idpTokenResult.getAccessToken().getRawString());
    }
}
