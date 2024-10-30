package de.servicehealth.epa4all.idp.action;

import de.gematik.idp.authentication.AuthenticationChallenge;
import de.gematik.idp.client.AuthenticatorClient;
import de.gematik.idp.client.data.AuthenticationResponse;
import de.gematik.idp.client.data.DiscoveryDocumentResponse;
import de.service.health.api.epa4all.MultiEpaService;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.service.health.api.serviceport.IKonnektorServicePortsAPI;
import de.servicehealth.model.SendAuthCodeSC200Response;
import de.servicehealth.model.SendAuthCodeSCtype;

import java.security.cert.X509Certificate;
import java.util.function.Consumer;

public class VauNpAction extends AbstractAuthAction {

    private final Consumer<String> authConsumer;

    public VauNpAction(
        MultiEpaService multiEpaService,
        IKonnektorServicePortsAPI servicePorts,
        AuthenticatorClient authenticatorClient,
        DiscoveryDocumentResponse discoveryDocumentResponse,
        Consumer<String> authConsumer
        ) {
        super(multiEpaService, servicePorts, authenticatorClient, discoveryDocumentResponse);
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
        AuthorizationSmcBApi authorizationSmcBApi = multiEpaService.getEpaAPI().getAuthorizationSmcBApi();
        SendAuthCodeSC200Response sendAuthCodeSC200Response = authorizationSmcBApi.sendAuthCodeSC(USER_AGENT, sendAuthCodeSC);
        authConsumer.accept(sendAuthCodeSC200Response.getVauNp());
    }
}
