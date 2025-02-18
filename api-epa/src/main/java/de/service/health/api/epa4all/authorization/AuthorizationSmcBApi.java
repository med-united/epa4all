package de.service.health.api.epa4all.authorization;

import de.servicehealth.model.ErrorType;
import de.servicehealth.model.GetNonce200Response;
import de.servicehealth.model.SendAuthCodeSC200Response;
import de.servicehealth.model.SendAuthCodeSCtype;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import static de.servicehealth.vau.VauClient.CLIENT_ID;
import static de.servicehealth.vau.VauClient.VAU_CLIENT_UUID;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;

@Path("/epa/authz/v1")
@Api(value = "/")
public interface AuthorizationSmcBApi extends de.servicehealth.api.AuthorizationSmcBApi {
    /**
     * (sendAuthorizationRequestSC) Send authorization request
     * Request authorization for a smartcard (SMC-B) based client.
     * **Client**:&lt;/br&gt; A client will receive a well-prepared redirection uri and parameters for the
     * authorization request towards the authenticator. A client shall invoke the authenticator and IDP flow
     * to receive an  authorization code for the subsequent _sendAuthCodeSC_ operation.  **Provider**:&lt;/br&gt;
     * The authorization server shall prepare a complete redirection uri and authorization request parameters
     * (PAR-URI) for the central smartcard IDP
     * | Conditions | Status code | Error code | Remarks |
     * |------------|-------------|------------|---------|
     * | Successful operation | 302 |||
     * | Request does not match schema | 400 | malformedRequest ||
     * | Invalid request | 403 | invalAuth | includes any error of Authorization Service which is not mapped to 500 internal Server error |
     * | Any other error | 500 | internalError | (see &#39;Retry interval&#39;) |   &lt;/br&gt;
     * | Post-conditions                       | Remarks |
     * |---------------------------------------|---------|
     * | none ||
     */
    @GET
    @Path("/send_authorization_request_sc")
    @Produces({"application/json"})
    @ApiOperation(value = "(sendAuthorizationRequestSC) Send authorization request", tags = {})
    @ApiResponses(value = {
        @ApiResponse(code = 302, message = "Found"),
        @ApiResponse(code = 400, message = "Bad Request", response = ErrorType.class),
        @ApiResponse(code = 403, message = "Forbidden.", response = ErrorType.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ErrorType.class)})
    Response sendAuthorizationRequestSCWithResponse(
        @HeaderParam(CLIENT_ID) String clientId,
        @HeaderParam(X_USER_AGENT) String xUseragent,
        @HeaderParam(X_BACKEND) String xBackend,
        @HeaderParam(VAU_CLIENT_UUID) String vauClientUuid
    );

    @GET
    @Path("/getNonce")
    @Produces({ "application/json" })
    @ApiOperation(value = "(getNonce) Generate nonce (random value) for an authorization request", tags={  })
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Ok.", response = GetNonce200Response.class),
        @ApiResponse(code = 400, message = "Bad Request", response = ErrorType.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ErrorType.class) })
    GetNonce200Response getNonce(
        @HeaderParam(CLIENT_ID) String clientId,
        @HeaderParam(X_USER_AGENT) String xUseragent,
        @HeaderParam(X_BACKEND) String xBackend,
        @HeaderParam(VAU_CLIENT_UUID) String vauClientUuid
    );

    @POST
    @Path("/send_authcode_sc")
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    @ApiOperation(value = "(sendAuthCodeSC) Send authorization code", tags={  })
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK, Successful login", response = SendAuthCodeSC200Response.class),
        @ApiResponse(code = 400, message = "Bad Request", response = ErrorType.class),
        @ApiResponse(code = 403, message = "Forbidden.", response = ErrorType.class),
        @ApiResponse(code = 409, message = "Conflict.", response = ErrorType.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ErrorType.class) })
    SendAuthCodeSC200Response sendAuthCodeSC(
        @HeaderParam(CLIENT_ID) String clientId,
        @HeaderParam(X_USER_AGENT) String xUseragent,
        @HeaderParam(X_BACKEND) String xBackend,
        @HeaderParam(VAU_CLIENT_UUID) String vauClientUuid,
        SendAuthCodeSCtype sendAuthCodeSCtype
    );
}
