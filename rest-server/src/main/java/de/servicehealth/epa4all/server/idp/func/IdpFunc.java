package de.servicehealth.epa4all.server.idp.func;

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.FaultMessage;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticate;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticateResponse;
import de.servicehealth.epa4all.server.serviceport.IKonnektorServicePortsAPI;
import lombok.Getter;

import java.util.function.Function;
import java.util.function.Supplier;

@Getter
public class IdpFunc {

    private final Supplier<ContextType> ctxSupplier;
    private final Function<ExternalAuthenticate, ExternalAuthenticateResponse> extAuthFunc;

    private IdpFunc(
        Supplier<ContextType> ctxSupplier,
        Function<ExternalAuthenticate, ExternalAuthenticateResponse> extAuthFunc
    ) {
        this.ctxSupplier = ctxSupplier;
        this.extAuthFunc = extAuthFunc;
    }

    public static IdpFunc init(IKonnektorServicePortsAPI servicePorts) {
        return new IdpFunc(
            servicePorts::getContextType,
            externalAuthenticate -> {
                try {
                    return servicePorts.getAuthSignatureService().externalAuthenticate(externalAuthenticate);
                } catch (FaultMessage e) {
                    throw new RuntimeException("Could not external authenticate", e);
                }
            }
        );
    }
}
