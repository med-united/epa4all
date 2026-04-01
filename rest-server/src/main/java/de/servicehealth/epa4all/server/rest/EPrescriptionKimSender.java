package de.servicehealth.epa4all.server.rest;

import de.servicehealth.epa4all.server.kim.KimSmtpConfig;
import de.servicehealth.epa4all.server.kim.KimSmtpService;
import de.servicehealth.epa4all.server.presription.KimContext;
import de.servicehealth.epa4all.server.presription.PrescriptionBundleService;
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
import org.hl7.fhir.r4.model.Bundle;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
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
    KimSmtpConfig kimConfig;

    @APIResponses({
        @APIResponse(responseCode = "200", description = "KIM email was sent"),
        @APIResponse(responseCode = "400", description = "Some parameter is invalid"),
        @APIResponse(responseCode = "404", description = "KIM address not found"),
        @APIResponse(responseCode = "500", description = "Internal server error"),
        @APIResponse(responseCode = "503", description = "Error while sending  KIM email")
    })
    @POST
    @Produces(TEXT_PLAIN)
    @Consumes("application/fhir+json")
    @Operation(summary = "Send KIM email with prescription")
    public Response send(
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)"
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        Bundle epaBundle
    ) throws Exception {
        Integer hash = Objects.hash("eprescription", konnektor, epaBundle);
        return deduplicatedCall("e-prescription-kim-sender", konnektor, hash, () -> {
            KimContext kimContext = prescriptionBundleService.prepareKimContextWithBundle(
                userRuntimeConfig, epaBundle
            );
            Map<String, String> headers = Map.of(
                kimConfig.getKimEprescriptionHeaderName(), kimConfig.getKimEprescriptionHeaderValue(),
                "X-KIM-Encounter-Id", UUID.randomUUID().toString()
            );
            String result = kimSmtpService.sendERezept(headers, kimContext.address(), kimContext.bundle(), null);
            return Response.ok(result).build();
        });
    }
}
