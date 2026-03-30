package de.servicehealth.epa4all.server.rest;

import de.servicehealth.epa4all.server.kim.KimLdapService;
import de.servicehealth.epa4all.server.kim.KimSmtpConfig;
import de.servicehealth.epa4all.server.kim.KimSmtpService;
import de.servicehealth.epa4all.server.presription.PrescriptionBundleService;
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

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

@SuppressWarnings("unused")
@RequestScoped
@Path("e-prescription-kim-sender")
public class EPrescriptionKimSender extends AbstractResource {

    @Inject
    PrescriptionBundleService prescriptionBundleService;

    @Inject
    KimSmtpService kimSmtpService;

    @Inject
    KimLdapService kimLdapService;

    @Inject
    KimSmtpConfig kimConfig;

    @APIResponses({
        @APIResponse(responseCode = "200", description = "KIM email was sent"),
        @APIResponse(responseCode = "400", description = "Some parameter is invalid"),
        @APIResponse(responseCode = "404", description = "KIM address not found"),
        @APIResponse(responseCode = "409", description = "MedicationRequest doesn't match Medication"),
        @APIResponse(responseCode = "500", description = "Internal server error"),
        @APIResponse(responseCode = "503", description = "Error while sending  KIM email")
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
        EPrescriptionRequest request
    ) throws Exception {
        Integer hash = Objects.hash("eprescription", request);
        return deduplicatedCall("e-prescription-kim-sender", null, hash, () -> {
            String kimAddress = kimLdapService.searchKimAddress(userRuntimeConfig, request.getPractitionerName());
            String bundle = prescriptionBundleService.buildPrescriptionRequestBundle(
                request.getEpaBundleBase64(), request.getSelectedMedicationId(), kimAddress
            );
            Map<String, String> headers = Map.of(
                kimConfig.getKimEprescriptionHeaderName(), kimConfig.getKimEprescriptionHeaderValue(),
                "X-KIM-Encounter-Id", UUID.randomUUID().toString()
            );
            String result = kimSmtpService.sendERezept(headers, kimAddress, bundle, null);
            return Response.ok(result).build();
        });
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @ApiModel(description = "Prescription request based on a medication selected from the ePA Medication List")
    public static class EPrescriptionRequest {

        @ApiModelProperty(value = "Base64-encoded full ePA $medication-list Bundle JSON", required = true)
        String epaBundleBase64;

        @ApiModelProperty(value = "ID of the Medication selected by the patient from the ePA list", required = true)
        String selectedMedicationId;

        @ApiModelProperty(
            value = "Practitioner display name for LDAP/VZD lookup of KIM address",
            required = true,
            example = "Tanja Freifrau Dåvid"
        )
        String practitionerName;
    }
}
