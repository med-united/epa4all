package de.servicehealth.epa4all.server.rest;

import de.servicehealth.api.epa4all.EpaAPI;
import de.servicehealth.api.epa4all.entitlement.EntitlementsFdvAPI;
import de.servicehealth.epa4all.server.vsd.VsdService;
import de.servicehealth.model.EntitlementClaimsResponseType;
import de.servicehealth.model.GetEntitlements200Response;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.time.Instant;
import java.util.Map;

import static de.servicehealth.epa4all.server.rest.consent.ConsentFunction.Medication;
import static de.servicehealth.logging.LogContext.resultMdcEx;
import static de.servicehealth.logging.LogField.INSURANT;
import static de.servicehealth.logging.LogField.KONNEKTOR;
import static de.servicehealth.logging.LogField.SMCB_HANDLE;
import static de.servicehealth.vau.VauClient.ACTOR_ID;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;

@SuppressWarnings("unused")
@RequestScoped
@Path("entitlements")
public class Entitlement extends AbstractResource {

    @Inject
    VsdService vsdService;

    @APIResponses({
        @APIResponse(responseCode = "200", description = "Entitlement was successfully set"),
        @APIResponse(responseCode = "400", description = "x-insurantid was not specified"),
        @APIResponse(responseCode = "404", description = "Patient is not found in any ePA"),
        @APIResponse(responseCode = "409", description = "ePA error"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Path("{actorId}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Get entitlements for Patient")
    public EntitlementClaimsResponseType getEntitlement(
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)"
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = X_INSURANT_ID, description = "Patient KVNR", required = true)
        @NotBlank @QueryParam(X_INSURANT_ID) String insurantId,
        @Parameter(name = ACTOR_ID, description = "ActorId", required = true)
        @NotBlank @PathParam(ACTOR_ID) String actorId
    ) throws Exception {
        EpaAPI epaApi = epaMultiService.findEpaAPI(insurantId);
        EntitlementsFdvAPI entitlementsFdvApi = epaApi.getEntitlementsFdvAPI();
        return entitlementsFdvApi.getEpaEntitlement(insurantId, epaConfig.getEpaUserAgent(), epaApi.getBackend(), actorId);
    }

    @APIResponses({
        @APIResponse(responseCode = "200", description = "Entitlement was successfully set"),
        @APIResponse(responseCode = "400", description = "x-insurantid was not specified"),
        @APIResponse(responseCode = "404", description = "Patient is not found in any ePA"),
        @APIResponse(responseCode = "409", description = "ePA error"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Get entitlements for Patient")
    public GetEntitlements200Response getEntitlements(
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)"
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = X_INSURANT_ID, description = "Patient KVNR", required = true)
        @NotBlank @QueryParam(X_INSURANT_ID) String insurantId
    ) throws Exception {
        EpaAPI epaApi = epaMultiService.findEpaAPI(insurantId);
        EntitlementsFdvAPI entitlementsFdvApi = epaApi.getEntitlementsFdvAPI();
        return entitlementsFdvApi.getEpaEntitlements(insurantId, epaConfig.getEpaUserAgent(), epaApi.getBackend());
    }

    @APIResponses({
        @APIResponse(responseCode = "200", description = "Entitlement was successfully set"),
        @APIResponse(responseCode = "204", description = "Entitlement is expired or empty, unable to set it due to error"),
        @APIResponse(responseCode = "400", description = "x-insurantid was not specified"),
        @APIResponse(responseCode = "403", description = "Patient did not give a consent"),
        @APIResponse(responseCode = "404", description = "Patient is not found in any ePA"),
        @APIResponse(responseCode = "409", description = "ePA error"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @POST
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Set entitlement for Patient")
    public Response setEntitlement(
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)"
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = X_INSURANT_ID, description = "Patient KVNR", required = true)
        @NotBlank @QueryParam(X_INSURANT_ID) String insurantId
    ) throws Exception {
        return resultMdcEx(Map.of(
            SMCB_HANDLE, smcbHandle,
            KONNEKTOR, konnektor == null ? "default" : konnektor,
            INSURANT, insurantId
        ), () -> {
            consentValidator.validate(insurantId, Medication);
            EpaContext epaContext = prepareEpaContext(insurantId);
            Instant expiry = epaContext.getEntitlementExpiry();
            return expiry == null ? Response.status(NO_CONTENT).build() : Response.ok().build();
        });
    }
}
