package de.servicehealth.epa4all.server.rest;

import de.servicehealth.api.epa4all.EpaAPI;
import de.servicehealth.epa4all.server.epa.EpaCallGuard;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.Objects;

import static de.servicehealth.logging.LogContext.resultMdcEx;
import static de.servicehealth.utils.ServerUtils.APPLICATION_PDF;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static de.servicehealth.vau.VauClient.X_SUBJECT;
import static jakarta.ws.rs.core.MediaType.TEXT_HTML;

@SuppressWarnings("unused")
@RequestScoped
@Path("render/v1")
public class MedicationRender extends AbstractResource {

    @Inject
    EpaCallGuard epaCallGuard;

    @APIResponses({
        @APIResponse(responseCode = "200", description = "eMP PDF rendered successfully"),
        @APIResponse(responseCode = "400", description = "x-insurantid is missing"),
        @APIResponse(responseCode = "404", description = "Patient is not found in any ePA"),
        @APIResponse(responseCode = "429", description = "duplicated call"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Path("emp/pdf")
    @Produces(APPLICATION_PDF)
    @Operation(summary = "Render eMP (medication plan) as PDF")
    public Response empPdf(
        @Parameter(name = X_KONNEKTOR, description = "IP of the target Konnektor (can be skipped for single-tenancy)")
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = X_INSURANT_ID, description = "Patient KVNR", required = true)
        @NotBlank @QueryParam(X_INSURANT_ID) String xInsurantId,
        @Parameter(name = X_SUBJECT, description = "Patient KVNR (FHIR compatible)")
        @QueryParam(X_SUBJECT) String subject
    ) throws Exception {
        return render("emp/pdf", xInsurantId, subject, RenderType.EMP_PDF);
    }

    @APIResponses({
        @APIResponse(responseCode = "200", description = "eML PDF rendered successfully"),
        @APIResponse(responseCode = "400", description = "x-insurantid is missing"),
        @APIResponse(responseCode = "404", description = "Patient is not found in any ePA"),
        @APIResponse(responseCode = "429", description = "duplicated call"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Path("eml/pdf")
    @Produces(APPLICATION_PDF)
    @Operation(summary = "Render eML (medication list) as PDF")
    public Response emlPdf(
        @Parameter(name = X_KONNEKTOR, description = "IP of the target Konnektor (can be skipped for single-tenancy)")
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = X_INSURANT_ID, description = "Patient KVNR", required = true)
        @NotBlank @QueryParam(X_INSURANT_ID) String xInsurantId,
        @Parameter(name = X_SUBJECT, description = "Patient KVNR (FHIR compatible)")
        @QueryParam(X_SUBJECT) String subject
    ) throws Exception {
        return render("eml/pdf", xInsurantId, subject, RenderType.EML_PDF);
    }

    @APIResponses({
        @APIResponse(responseCode = "200", description = "eML XHTML rendered successfully"),
        @APIResponse(responseCode = "400", description = "x-insurantid is missing"),
        @APIResponse(responseCode = "404", description = "Patient is not found in any ePA"),
        @APIResponse(responseCode = "429", description = "duplicated call"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Path("eml/xhtml")
    @Produces(TEXT_HTML)
    @Operation(summary = "Render eML (medication list) as XHTML")
    public Response emlXhtml(
        @Parameter(name = X_KONNEKTOR, description = "IP of the target Konnektor (can be skipped for single-tenancy)")
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = X_INSURANT_ID, description = "Patient KVNR", required = true)
        @NotBlank @QueryParam(X_INSURANT_ID) String xInsurantId,
        @Parameter(name = X_SUBJECT, description = "Patient KVNR (FHIR compatible)")
        @QueryParam(X_SUBJECT) String subject
    ) throws Exception {
        return render("eml/xhtml", xInsurantId, subject, RenderType.EML_XHTML);
    }

    private Response render(String renderPath, String xInsurantId, String subject, RenderType type) throws Exception {
        String insurantId = subject != null ? subject : xInsurantId;
        String backend = epaMultiService.findEpaAPI(insurantId).getBackend();
        Integer hash = Objects.hash(renderPath, insurantId);
        return deduplicatedCall(renderPath, null, hash, () -> epaCallGuard.callAndRetry(backend, () -> {
            EpaContext epaContext = prepareEpaContext(insurantId);
            return resultMdcEx(epaContext.getMdcMap(), () -> {
                EpaAPI epaAPI = epaMultiService.findEpaAPI(epaContext.getInsurantId());
                return switch (type) {
                    case EMP_PDF -> epaAPI.getRenderProxy().getEmpPdf(epaContext.getXHeaders());
                    case EML_PDF -> epaAPI.getRenderProxy().getEmlPdf(epaContext.getXHeaders());
                    case EML_XHTML -> epaAPI.getRenderProxy().getEmlXhtml(epaContext.getXHeaders());
                };
            });
        }));
    }

    private enum RenderType { EMP_PDF, EML_PDF, EML_XHTML }
}
