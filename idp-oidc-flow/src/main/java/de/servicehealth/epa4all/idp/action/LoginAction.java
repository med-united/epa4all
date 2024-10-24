package de.servicehealth.epa4all.idp.action;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.IdpTokenResult;
import de.gematik.idp.client.data.AuthenticationResponse;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.gematik.idp.client.data.TokenRequest;
import de.service.health.api.epa4all.MultiEpaService;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.service.health.api.serviceport.IKonnektorServicePortsAPI;
import org.apache.commons.codec.digest.DigestUtils;

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
        MultiEpaService multiEpaService,
        IKonnektorServicePortsAPI servicePorts,
        AuthenticatorClient authenticatorClient,
        DiscoveryDocumentResponse discoveryDocumentResponse,
        Consumer<String> authConsumer
    ) {
        super(multiEpaService, servicePorts, authenticatorClient, discoveryDocumentResponse);

        this.idpClientId = idpClientId;
        this.idpAuthRequestRedirectUrl = idpAuthRequestRedirectUrl;
        this.authConsumer = authConsumer;
    }

    @Override
    public void execute(
        AuthenticationChallenge authChallenge,
        X509Certificate smcbAuthCert,
        String codeChallenge,
        String smcbHandle,
        String clientAttest,
        String signatureType
    ) {
        AuthenticationResponse authenticationResponse = processAuthenticationChallenge(
            smcbHandle, authChallenge, smcbAuthCert, signatureType
        );

        String codeVerifier = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(DigestUtils.sha256(codeChallenge));

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
