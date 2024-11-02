package de.servicehealth.epa4all.server.idp.func;

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.FaultMessage;
import de.health.service.config.api.UserRuntimeConfig;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
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

    private final MultiEpaService multiEpaService;
    private final MultiKonnektorService multiKonnektorService;

    @Inject
    public IdpFuncer(
        MultiEpaService multiEpaService,
        MultiKonnektorService multiKonnektorService
    ) {
        this.multiEpaService = multiEpaService;
        this.multiKonnektorService = multiKonnektorService;
    }

    public IdpFunc init(String xInsurantId, UserRuntimeConfig userRuntimeConfig) {
        multiEpaService.setXInsurantid(xInsurantId);
        EpaAPI epaAPI = multiEpaService.getEpaAPI();
        AuthorizationSmcBApi authorizationSmcBApi = epaAPI.getAuthorizationSmcBApi();
        IKonnektorServicePortsAPI servicePorts = multiKonnektorService.getServicePorts(userRuntimeConfig);
        return new IdpFunc(
            () -> authorizationSmcBApi.getNonce(USER_AGENT).getNonce(),
            servicePorts::getContextType,
            () -> authorizationSmcBApi.sendAuthorizationRequestSCWithResponse(USER_AGENT),
            externalAuthenticate -> {
                try {
                    return servicePorts.getAuthSignatureService().externalAuthenticate(externalAuthenticate);
                } catch (FaultMessage e) {
                    throw new RuntimeException("Could not external authenticate", e);
                }
            },
            sendAuthCodeSC -> authorizationSmcBApi.sendAuthCodeSC(USER_AGENT, sendAuthCodeSC)
        );
    }
}
