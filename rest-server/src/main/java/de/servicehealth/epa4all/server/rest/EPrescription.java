package de.servicehealth.epa4all.server.rest;

import de.servicehealth.epa4all.server.kim.KimSmtpService;
import de.servicehealth.epa4all.server.presription.PrescriptionBundleService;
import de.servicehealth.epa4all.server.presription.requestdata.OrganizationData;
import de.servicehealth.epa4all.server.presription.requestdata.PatientData;
import de.servicehealth.epa4all.server.presription.requestdata.PractitionerData;
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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static java.nio.charset.StandardCharsets.UTF_8;

@SuppressWarnings("unused")
@RequestScoped
@Path("e-prescription-kim-sender")
public class EPrescription extends AbstractResource {

    @Inject
    PrescriptionBundleService prescriptionBundleService;

    @Inject
    KimSmtpService kimSmtpService;

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
        EPrescriptionDto request
    ) throws Exception {
        Integer hash = Objects.hash("eprescription", request);
        return deduplicatedCall("e-prescription-kim-sender", null, hash, () -> {
            String epaMedicationJson = new String(Base64.getDecoder().decode(request.getEpaMedicationBase64()), UTF_8);
            String bundle = prescriptionBundleService.buildPrescriptionRequestBundleFromEpa(
                epaMedicationJson,
                request.getDosage(),
                request.getPatientData(),
                request.getOrganizationData(),
                request.getPractitionerData()
            );
            Map<String, String> headers = Map.of(
                "Dienstkennung", "eRezept;Rezeptanforderung;1.0",
                "X-KIM-Encounter-Id", UUID.randomUUID().toString()
            );
            String result = kimSmtpService.sendERezept(headers, bundle, null);
            return Response.ok(result).build();
        });
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @ApiModel(description = "EPrescription DTO")
    public static class EPrescriptionDto {

        @ApiModelProperty(value = "epaMedicationBase64", required = true)
        String epaMedicationBase64;

        @ApiModelProperty(value = "Dosage instruction text", example = "1-0-1-0")
        String dosage;

        PatientData patientData;
        OrganizationData organizationData;
        PractitionerData practitionerData;
    }
}
