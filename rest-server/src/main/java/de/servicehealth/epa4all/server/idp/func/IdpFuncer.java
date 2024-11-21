package de.servicehealth.epa4all.server.idp.func;

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.FaultMessage;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.server.serviceport.IKonnektorServicePortsAPI;
import de.servicehealth.epa4all.server.serviceport.MultiKonnektorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;

@Getter
@ApplicationScoped
public class IdpFuncer {

    private final MultiKonnektorService multiKonnektorService;

    @Inject
    public IdpFuncer(MultiKonnektorService multiKonnektorService) {
        this.multiKonnektorService = multiKonnektorService;
    }

    public IdpFunc init(
        String userAgent,
        IKonnektorServicePortsAPI servicePorts,
        AuthorizationSmcBApi authorizationSmcBApi
    ) {
        return new IdpFunc(
            servicePorts::getContextType,
            externalAuthenticate -> {
                try {
                    return servicePorts.getAuthSignatureService().externalAuthenticate(externalAuthenticate);
                } catch (FaultMessage e) {
                    throw new RuntimeException("Could not external authenticate", e);
                }
            },
            () -> authorizationSmcBApi.getNonce(userAgent).getNonce(),
            () -> authorizationSmcBApi.sendAuthorizationRequestSCWithResponse(userAgent),
            sendAuthCodeSC -> authorizationSmcBApi.sendAuthCodeSC(userAgent, sendAuthCodeSC)
        );
    }
}
