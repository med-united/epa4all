package de.servicehealth.epa4all.server.idp.func;

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.FaultMessage;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticate;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticateResponse;
import de.service.health.api.epa4all.authorization.AuthorizationSmcBApi;
import de.servicehealth.epa4all.server.serviceport.IKonnektorServicePortsAPI;
import de.servicehealth.model.SendAuthCodeSC200Response;
import de.servicehealth.model.SendAuthCodeSCtype;
import jakarta.ws.rs.core.Response;
import lombok.Getter;

import java.util.function.Function;
import java.util.function.Supplier;

@Getter
public class IdpFunc {

    private final Supplier<String> nonceSupplier;
    private final Supplier<ContextType> ctxSupplier;
    private final Supplier<Response> authorizationResponseSupplier;
    private final Function<ExternalAuthenticate, ExternalAuthenticateResponse> extAuthFunc;
    private final Function<SendAuthCodeSCtype, SendAuthCodeSC200Response> sendAuthCodeFunc;

    private IdpFunc(
        Supplier<ContextType> ctxSupplier,
        Function<ExternalAuthenticate, ExternalAuthenticateResponse> extAuthFunc,
        Supplier<String> nonceSupplier,
        Supplier<Response> authorizationResponseSupplier,
        Function<SendAuthCodeSCtype, SendAuthCodeSC200Response> sendAuthCodeFunc
    ) {
        this.ctxSupplier = ctxSupplier;
        this.extAuthFunc = extAuthFunc;
        this.nonceSupplier = nonceSupplier;
        this.authorizationResponseSupplier = authorizationResponseSupplier;
        this.sendAuthCodeFunc = sendAuthCodeFunc;
    }

    public static IdpFunc init(
        String clientId,
        String userAgent,
        String backend,
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
            () -> authorizationSmcBApi.getNonce(clientId, userAgent, backend).getNonce(),
            () -> authorizationSmcBApi.sendAuthorizationRequestSCWithResponse(clientId, userAgent, backend),
            sendAuthCodeSC -> authorizationSmcBApi.sendAuthCodeSC(clientId, userAgent, backend, sendAuthCodeSC)
        );
    }
}
