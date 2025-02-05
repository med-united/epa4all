package de.servicehealth.epa4all.server.idp.action;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.data.AuthenticationResponse;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.servicehealth.epa4all.server.idp.func.IdpFunc;
import de.servicehealth.model.SendAuthCodeSC200Response;
import de.servicehealth.model.SendAuthCodeSCtype;

import java.security.cert.X509Certificate;
import java.util.function.Consumer;

public class VauNpAction extends AbstractAuthAction {

    private final Consumer<SendAuthCodeSCtype> authConsumer;

    public VauNpAction(
        AuthenticatorClient authenticatorClient,
        DiscoveryDocumentResponse discoveryDocumentResponse,
        Consumer<SendAuthCodeSCtype> authConsumer,
        IdpFunc idpFunc
    ) {
        super(idpFunc, authenticatorClient, discoveryDocumentResponse);
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
        authConsumer.accept(sendAuthCodeSC);
    }
}
