package de.servicehealth.api.epa4all.entitlement;

import de.servicehealth.api.EntitlementsEPaFdVApi;
import de.servicehealth.model.EntitlementClaimsResponseType;
import de.servicehealth.model.ErrorType;
import de.servicehealth.model.GetEntitlements200Response;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;

import static de.servicehealth.vau.VauClient.ACTOR_ID;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;

public interface EntitlementsFdvAPI extends EntitlementsEPaFdVApi {

    /**
     * (getEntitlement) Get a single specific entitlement
     * Get a specific granted and not expired entitlement of the health record.&lt;/br&gt; This operation is limited to entitled users of role oid_versicherter.   **Client**:&lt;/br&gt; no recommendations.  **Provider**:&lt;/br&gt; The response shall contain the entitlement related to _actorId_ if available  The operation shall NOT consider the static entitlements for a response (even if stored in SecureAdminStorage).  | Conditions | Status code | Error code | Remarks | |------------|-------------|------------|---------| | Successful operation | 200 ||| | Request does not match schema | 400 | malformedRequest || | Requestor has no valid entitlement | 403 | notEntitled || | Requestor role is not _oid_versicherter_ | 403 | invalidOid || | Health record does not exist | 404 | noHealthRecord | _insurantid_ unknown | | Entitlement (_actorId_) does not exist | 404 | noResource | applies also if _actorId_ refers to a static entitlement | | Health record is not in state ACTIVATED | 409 | statusMismatch || | Any other error | 500 | internalError ||   &lt;/br&gt; | Postconditions                        | Remarks | |---------------------------------------|---------| | none ||
     *
     */
    @GET
    @Path("/{actorId}")
    @Produces({ "application/json" })
    @ApiOperation(value = "(getEntitlement) Get a single specific entitlement", tags={  })
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = EntitlementClaimsResponseType.class),
        @ApiResponse(code = 400, message = "Bad Request.", response = ErrorType.class),
        @ApiResponse(code = 403, message = "Forbidden.", response = ErrorType.class),
        @ApiResponse(code = 404, message = "Not found.", response = ErrorType.class),
        @ApiResponse(code = 409, message = "Conflict.", response = ErrorType.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ErrorType.class) })
    EntitlementClaimsResponseType getEpaEntitlement(
        @HeaderParam(X_INSURANT_ID)  String xInsurantid,
        @HeaderParam(X_USER_AGENT)  String xUseragent,
        @HeaderParam(X_BACKEND)  String xBackend,
        @PathParam(ACTOR_ID) String actorId
    );

    /**
     * (getEntitlements) Get a list of all granted entitlements, their related user and validity period
     * Get a list of actual granted entitlements of the health record.&lt;/br&gt; This operation is limited to entitled users of role oid_versicherter.  **Provider**:&lt;/br&gt; The returned list shall contain only entitlements not expired (_validTo_).&lt;/br&gt;  The operation shall NOT consider the static entitlements for a response (even if stored in SecureAdminStorage).   | Conditions | Status code | Error code | Remarks | |------------|-------------|------------|---------| | Successful operation | 200 ||| | Request does not match schema | 400 | malformedRequest || | Requestor has no valid entitlement | 403 | notEntitled || | Requestor role is not _oid_versicherter_ | 403 | invalidOid || | Health record does not exist | 404 | noHealthRecord | _insurantid_ unknown | | Health record is not in state ACTIVATED | 409 | statusMismatch || | Any other error | 500 | internalError ||   &lt;/br&gt; | Postconditions                        | Remarks | |---------------------------------------|---------| | none ||
     *
     */
    @GET
    @Produces({ "application/json" })
    @ApiOperation(value = "(getEntitlements) Get a list of all granted entitlements, their related user and validity period", tags={  })
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK. Returns a list of all entitlements", response = GetEntitlements200Response.class),
        @ApiResponse(code = 400, message = "Bad Request.", response = ErrorType.class),
        @ApiResponse(code = 403, message = "Forbidden.", response = ErrorType.class),
        @ApiResponse(code = 404, message = "Not found.", response = ErrorType.class),
        @ApiResponse(code = 409, message = "Conflict.", response = ErrorType.class),
        @ApiResponse(code = 500, message = "Internal Server Error", response = ErrorType.class) })
    GetEntitlements200Response getEpaEntitlements(
        @HeaderParam(X_INSURANT_ID)  String xInsurantid,
        @HeaderParam(X_USER_AGENT)  String xUseragent,
        @HeaderParam(X_BACKEND)  String xBackend
    );
}
