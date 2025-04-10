package de.servicehealth.api.epa4all.entitlement;

import de.servicehealth.model.EntitlementRequestType;
import de.servicehealth.model.ErrorType;
import de.servicehealth.model.ValidToResponseType;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;

import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;

public interface EntitlementsApi extends de.servicehealth.api.EntitlementsApi {

    @POST
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    @ApiOperation(value = "(setEntitlementPs) Set a single entitlement with proof of audit evidence", tags={  })
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Created. User is entitled", response = ValidToResponseType.class),
        @ApiResponse(code = 400, message = "Bad Request.", response = ErrorType.class),
        @ApiResponse(code = 403, message = "Forbidden.", response = ErrorType.class),
        @ApiResponse(code = 404, message = "Not found.", response = ErrorType.class),
        @ApiResponse(code = 409, message = "Conflict.", response = ErrorType.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ErrorType.class) })
    ValidToResponseType setEntitlementPs(
        @HeaderParam(X_INSURANT_ID)  String xInsurantid,
        @HeaderParam(X_USER_AGENT)  String xUseragent,
        @HeaderParam(X_BACKEND)  String xBackend,
        // @HeaderParam(VAU_NP)  String vauNp,
        @HeaderParam("User-Agent")  String userAgent,
        @HeaderParam("Connection")  String connection,
        @HeaderParam("Upgrade")  String upgrade,
        EntitlementRequestType entitlementRequestType
    );
}
