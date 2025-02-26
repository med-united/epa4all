package de.servicehealth.epa4all.server.rest;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.service.health.api.epa4all.EpaAPI;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.rest.exception.EpaClientError;
import de.servicehealth.epa4all.server.vsd.VsdService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.time.Instant;
import java.util.Base64;

import static de.servicehealth.epa4all.server.vsd.VsdResponseFile.extractInsurantId;
import static de.servicehealth.epa4all.server.vsd.VsdService.buildSyntheticVSDResponse;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;

@SuppressWarnings("unused")
@RequestScoped
@Path("vsd")
public class Vsd extends AbstractResource {

    @Inject
    VsdService vsdService;

    @APIResponses({
        @APIResponse(responseCode = "200", description = "The patient entitlement was successfully created"),
        @APIResponse(responseCode = "400", description = "InsurantId was not extracted"),
        @APIResponse(responseCode = "409", description = "ePA error response"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @POST
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    @Path("pnw")
    @Operation(summary = "Set entitlement for KVNR using PNW")
    public Response setEntitlement(
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)"
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = X_INSURANT_ID, description = "Patient KVNR", required = true)
        @QueryParam(X_INSURANT_ID) String xInsurantId,
        byte[] base64EncodedBody
    ) throws Exception {
        byte[] pruefungsnachweis = Base64.getDecoder().decode(base64EncodedBody);
        ReadVSDResponse readVSDResponse = buildSyntheticVSDResponse(null, pruefungsnachweis);
        String insurantId = extractInsurantId(readVSDResponse, xInsurantId);
        if (insurantId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new EpaClientError("Unable to resolve 'x-insurantid'"))
                .build();
        } else {
            vsdService.saveVsdFile(telematikId, insurantId, readVSDResponse);
            InsuranceData insuranceData = insuranceDataService.getData(telematikId, insurantId);
            EpaAPI epaApi = epaMultiService.findEpaAPI(insurantId);
            String userAgent = epaConfig.getEpaUserAgent();
            Instant entitlementExpiry = entitlementService.setEntitlement(
                userRuntimeConfig, insuranceData, epaApi, telematikId, userAgent, smcbHandle
            );
            return Response.ok().entity(entitlementExpiry).build();
        }
    }
    
    @APIResponses({
        @APIResponse(responseCode = "201", description = "The patient folder was successfully created"),
        @APIResponse(responseCode = "400", description = "InsurantId was not extracted"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @POST
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    @Path("kvnr")
    @Operation(summary = "Create KVNR folder in the WebDav storage")
    public Response setEntitlement(
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)"
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = X_INSURANT_ID, description = "Patient KVNR", required = true)
        @QueryParam(X_INSURANT_ID) String xInsurantId
    ) throws Exception {
        ReadVSDResponse readVSDResponse = buildSyntheticVSDResponse();
        if (xInsurantId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new EpaClientError("Unable to resolve 'x-insurantid'"))
                .build();
        } else {
            vsdService.saveVsdFile(telematikId, xInsurantId, readVSDResponse);
            prepareEpaContext(xInsurantId);
            return Response.status(Response.Status.CREATED).build();
        }
    }
}
