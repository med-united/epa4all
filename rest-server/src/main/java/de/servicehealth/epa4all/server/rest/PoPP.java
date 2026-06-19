package de.servicehealth.epa4all.server.rest;

import de.servicehealth.api.epa4all.EpaAPI;
import de.servicehealth.api.epa4all.EpaMultiService;
import de.servicehealth.model.EntitlementRequestTypeV2;
import de.servicehealth.model.ValidToResponseType;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.UUID;

import static de.servicehealth.epa4all.server.rest.filter.PoppUtils.extractInsurantId;
import static jakarta.ws.rs.core.MediaType.WILDCARD;

@SuppressWarnings("unused")
@RequestScoped
@Path("popp")
public class PoPP extends AbstractResource {

    @Inject
    protected EpaMultiService epaMultiService;

    @APIResponses({
        @APIResponse(responseCode = "200", description = "The patient entitlement was successfully created"),
        @APIResponse(responseCode = "400", description = "x-insurantid is invalid"),
        @APIResponse(responseCode = "409", description = "ePA error"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @POST
    @Consumes(WILDCARD)
    @Produces(WILDCARD)
    @Path("token")
    @Operation(summary = "Set entitlement for KVNR using PNW")
    public Response setEntitlement(
        String poppToken
    ) throws Exception {
        String insurantId = extractInsurantId(poppToken);
        EpaContext epaContext = prepareEpaContext(insurantId);
        EpaAPI epaAPI = epaMultiService.findEpaAPI(insurantId);
        EntitlementRequestTypeV2 entitlementRequestTypeV2 = new EntitlementRequestTypeV2();
        entitlementRequestTypeV2.setPopp(poppToken);
        ValidToResponseType validToResponseType = epaAPI.getEntitlementsAPI().setEntitlementPsV2(insurantId, epaMultiService.getEpaConfig().getEpaUserAgent(), entitlementRequestTypeV2, UUID.randomUUID());
        return Response.ok(validToResponseType).build();

    }
}
