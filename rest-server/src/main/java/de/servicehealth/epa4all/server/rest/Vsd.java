package de.servicehealth.epa4all.server.rest;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.service.health.api.epa4all.EpaAPI;
import de.servicehealth.epa4all.cxf.provider.VauException;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.pnw.PnwException;
import de.servicehealth.epa4all.server.pnw.PnwResponse;
import de.servicehealth.epa4all.server.rest.exception.EpaClientError;
import de.servicehealth.epa4all.server.vsd.VsdService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static de.servicehealth.epa4all.server.vsd.VsdResponseFile.extractInsurantId;
import static de.servicehealth.epa4all.server.vsd.VsdService.buildSyntheticVSDResponse;
import static de.servicehealth.logging.LogContext.resultMdcEx;
import static de.servicehealth.logging.LogField.INSURANT;
import static de.servicehealth.logging.LogField.KONNEKTOR;
import static de.servicehealth.logging.LogField.SMCB_HANDLE;
import static de.servicehealth.utils.ServerUtils.getOriginalCause;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;
import static jakarta.ws.rs.core.MediaType.WILDCARD;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CREATED;

@SuppressWarnings("unused")
@RequestScoped
@Path("vsd")
public class Vsd extends AbstractResource {

    @Inject
    VsdService vsdService;

    @APIResponses({
        @APIResponse(responseCode = "200", description = "The patient entitlement was successfully created"),
        @APIResponse(responseCode = "400", description = "InsurantId was not extracted"),
        @APIResponse(responseCode = "409", description = "ePA error"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @POST
    @Consumes(WILDCARD)
    @Produces(APPLICATION_XML)
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
        @Parameter(name = "startDate", description = "Patient entitlement start date", required = true)
        @QueryParam("startDate") String startDate,
        @Parameter(name = "street", description = "Patient street", required = true)
        @QueryParam("street") String street,
        byte[] base64EncodedBody
    ) throws Exception {
        byte[] pruefungsnachweis = Base64.getDecoder().decode(base64EncodedBody);
        ReadVSDResponse readVSDResponse = buildSyntheticVSDResponse(null, pruefungsnachweis);
        String insurantId = extractInsurantId(readVSDResponse, xInsurantId);
        if (insurantId == null) {
            return Response.status(BAD_REQUEST)
                .entity(new EpaClientError("Unable to resolve 'x-insurantid'"))
                .build();
        } else {
            return resultMdcEx(Map.of(
                SMCB_HANDLE, smcbHandle,
                KONNEKTOR, konnektor,
                INSURANT, insurantId
            ), () -> {
                try {
                    EpaAPI epaApi = epaMultiService.findEpaAPI(insurantId);
                    vsdService.saveVsdFile(telematikId, insurantId, readVSDResponse);
                    InsuranceData insuranceData = insuranceDataService.getData(telematikId, insurantId);
                    String userAgent = epaConfig.getEpaUserAgent();
                    Instant expiry = entitlementService.setEntitlement(
                        userRuntimeConfig, insuranceData, epaApi, telematikId, userAgent, smcbHandle
                    );
                    PnwResponse pnwResponse = new PnwResponse(insurantId, startDate, expiry.toString(), street, null);
                    return Response.ok().entity(pnwResponse).build();
                } catch (Exception e) {
                    Throwable cause = getOriginalCause(e);
                    if (cause instanceof VauException vauException) {
                        throw new PnwException(insurantId, "<![CDATA[" + vauException.getJsonNode().toString()  + "]]>");
                    } else {
                        throw new PnwException(insurantId, cause.getMessage());
                    }
                }
            });
        }
    }
    
    @APIResponses({
        @APIResponse(responseCode = "201", description = "The patient folder was successfully created"),
        @APIResponse(responseCode = "400", description = "InsurantId was not extracted"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @POST
    @Consumes(WILDCARD)
    @Produces(WILDCARD)
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
            return Response.status(BAD_REQUEST)
                .entity(new EpaClientError("Unable to resolve 'x-insurantid'"))
                .build();
        } else {
            vsdService.saveVsdFile(telematikId, xInsurantId, readVSDResponse);
            prepareEpaContext(xInsurantId);
            return Response.status(CREATED).build();
        }
    }
}