package de.servicehealth.epa4all.idp.authorization;

import de.servicehealth.model.ErrorType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

@Path("/epa/authz/v1")
@Api(value = "/", description = "")
public interface AuthorizationSmcBApi extends de.servicehealth.api.AuthorizationSmcBApi {
   /**
     * (sendAuthorizationRequestSC) Send authorization request
     *
     * Request authorization for a smartcard (SMC-B) based client.  **Client**:&lt;/br&gt; A client will receive a well prepared redirection uri and parameters for the authoriation request towards the authenticator. A client shall invoke the authenticator and IDP flow to revceive an  authorization code for the subsequent _sendAuthCodeSC_ operation.  **Provider**:&lt;/br&gt; The authorization server shall prepare a complete redirection uri and authorization request parameters  (PAR-URI) for the central smartcard IDP    | Conditions | Status code | Error code | Remarks | |------------|-------------|------------|---------| | Successful operation | 302 ||| | Request does not match schema | 400 | malformedRequest || | Invalid request | 403 | invalAuth | includes any error of Authorization Service which is not mapped to 500 internal Server error | | Any other error | 500 | internalError | (see &#39;Retry interval&#39;) |   &lt;/br&gt; | Postconditions                        | Remarks | |---------------------------------------|---------| | none || 
     *
     */
    @GET
    @Path("/send_authorization_request_sc")
    @Produces({ "application/json" })
    @ApiOperation(value = "(sendAuthorizationRequestSC) Send authorization request", tags={  })
    @ApiResponses(value = { 
        @ApiResponse(code = 302, message = "Found"),
        @ApiResponse(code = 400, message = "Bad Request", response = ErrorType.class),
        @ApiResponse(code = 403, message = "Forbidden.", response = ErrorType.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ErrorType.class) })
    public Response sendAuthorizationRequestSCWithResponse(@HeaderParam("x-useragent")  String xUseragent); 
}