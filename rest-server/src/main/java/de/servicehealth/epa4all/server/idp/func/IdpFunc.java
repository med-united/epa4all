package de.servicehealth.epa4all.server.idp.func;

import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticate;
import de.gematik.ws.conn.signatureservice.v7.ExternalAuthenticateResponse;
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

    public IdpFunc(
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
}
