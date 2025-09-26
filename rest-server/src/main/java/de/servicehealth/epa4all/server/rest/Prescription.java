package de.servicehealth.epa4all.server.rest;

import de.servicehealth.epa4all.server.presription.PrescriptionService;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.Objects;

import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

@SuppressWarnings("unused")
@RequestScoped
@Path("prescription")
public class Prescription extends AbstractResource {

    @Inject
    PrescriptionService prescriptionService;

    @APIResponses({
        @APIResponse(responseCode = "200", description = "KIM email was sent"),
        @APIResponse(responseCode = "400", description = "Some parameter is invalid"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @POST
    @Produces(TEXT_PLAIN)
    @Consumes(APPLICATION_JSON)
    @Operation(summary = "Send KIM email with prescription")
    public Response send(
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)"
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = X_INSURANT_ID, description = "Patient KVNR", required = true)
        @NotBlank @QueryParam(X_INSURANT_ID) String insurantId,
        PrescriptionDto request
    ) throws Exception {
        String equipment = request.getEquipment();
        Integer hash = Objects.hash("prescription", equipment, insurantId);
        return deduplicatedCall("prescription", equipment, hash, () -> {
            String res = prescriptionService.sendKimEmail(
                userRuntimeConfig, telematikId, smcbHandle, insurantId,
                equipment, request.getLanr(), request.getNamePrefix(),
                request.getBsnr(), request.getPhone(), request.getNote()
            );
            return Response.ok(res).build();
        });
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @ApiModel(description="Prescription DTO")
    public static class PrescriptionDto {
        @ApiModelProperty(value = "Selected equipment", required = true)
        String equipment;

        @ApiModelProperty(value = "Selected lanr", required = true)
        String lanr;

        @ApiModelProperty(value = "Selected name prefix", required = true)
        String namePrefix;

        @ApiModelProperty(value = "Selected bsnr", required = true)
        String bsnr;

        @ApiModelProperty(value = "Selected phone", required = true)
        String phone;

        @ApiModelProperty(value = "Note to pharmacy", required = true)
        String note;
    }
}