package de.servicehealth.epa4all.server.rest;

import de.servicehealth.epa4all.server.presription.PrescriptionService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.Objects;

import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

@SuppressWarnings("unused")
@RequestScoped
@Path("prescription")
public class Prescription extends AbstractResource {

    @Inject
    PrescriptionService prescriptionService;

    @APIResponses({
        @APIResponse(responseCode = "200", description = "KIM email was sent"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @POST
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Send KIM email with prescription")
    public Response send(
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)"
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = X_INSURANT_ID, description = "Patient KVNR", required = true)
        @QueryParam(X_INSURANT_ID) String insurantId,
        @Parameter(name = "equipment", description = "Selected equipment", required = true)
        @QueryParam("equipment") String equipment,
        @Parameter(name = "lanr", description = "Selected lanr", required = true)
        @QueryParam("lanr") String lanr,
        @Parameter(name = "namePrefix", description = "Selected namePrefix", required = true)
        @QueryParam("namePrefix") String namePrefix,
        @Parameter(name = "bsnr", description = "Selected bsnr", required = true)
        @QueryParam("bsnr") String bsnr,
        @Parameter(name = "phone", description = "Selected phone", required = true)
        @QueryParam("phone") String phone
    ) throws Exception {
        Integer hash = Objects.hash("prescription", equipment, insurantId);
        return deduplicatedCall("prescription", equipment, hash, () -> {
            String res = prescriptionService.sendKimEmail(
                userRuntimeConfig, telematikId, smcbHandle, insurantId, equipment, lanr, namePrefix, bsnr, phone
            );
            return Response.ok(res).build();
        });
    }
}