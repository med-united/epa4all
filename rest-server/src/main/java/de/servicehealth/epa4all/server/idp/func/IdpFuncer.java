package de.servicehealth.epa4all.server.idp.func;

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.FaultMessage;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.server.serviceport.IKonnektorServicePortsAPI;
import de.servicehealth.epa4all.server.serviceport.MultiKonnektorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;

import static de.servicehealth.epa4all.cxf.client.ClientFactory.USER_AGENT;

@Getter
@ApplicationScoped
public class IdpFuncer {

    private final MultiKonnektorService multiKonnektorService;

    @Inject
    public IdpFuncer(MultiKonnektorService multiKonnektorService) {
        this.multiKonnektorService = multiKonnektorService;
    }

    public IdpFunc init(AuthorizationSmcBApi authorizationSmcBApi, IKonnektorServicePortsAPI servicePorts) {
        return new IdpFunc(
            servicePorts::getContextType,
            externalAuthenticate -> {
                try {
                    return servicePorts.getAuthSignatureService().externalAuthenticate(externalAuthenticate);
                } catch (FaultMessage e) {
                    throw new RuntimeException("Could not external authenticate", e);
                }
            },
            () -> authorizationSmcBApi.getNonce(USER_AGENT).getNonce(),
            () -> authorizationSmcBApi.sendAuthorizationRequestSCWithResponse(USER_AGENT),
            sendAuthCodeSC -> authorizationSmcBApi.sendAuthCodeSC(USER_AGENT, sendAuthCodeSC)
        );
    }
}
