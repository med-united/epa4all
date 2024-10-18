package de.servicehealth.epa4all.idp.action;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.data.AuthenticationResponse;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.servicehealth.epa4all.idp.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.serviceport.IServicePortAggregator;
import de.servicehealth.model.SendAuthCodeSC200Response;
import de.servicehealth.model.SendAuthCodeSCtype;

import java.security.cert.X509Certificate;
import java.util.function.Consumer;

public class VauNpAction extends AbstractAuthAction {

    private final Consumer<String> authConsumer;

    public VauNpAction(
        IServicePortAggregator servicePorts,
        AuthenticatorClient authenticatorClient,
        AuthorizationSmcBApi authorizationService,
        DiscoveryDocumentResponse discoveryDocumentResponse,
        Consumer<String> authConsumer
        ) {
        super(servicePorts, authenticatorClient, authorizationService, discoveryDocumentResponse);
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

        SendAuthCodeSCtype sendAuthCodeSC = new SendAuthCodeSCtype();
        sendAuthCodeSC.setAuthorizationCode(authenticationResponse.getCode());
        sendAuthCodeSC.setClientAttest(clientAttest);
        SendAuthCodeSC200Response sendAuthCodeSC200Response = authorizationService.sendAuthCodeSC(USER_AGENT, sendAuthCodeSC);
        authConsumer.accept(sendAuthCodeSC200Response.getVauNp());
    }
}
