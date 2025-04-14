package de.servicehealth.epa4all.server.rest;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.domain.eventservice.card.Card;
import de.health.service.cetp.domain.fault.CetpFault;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.List;

import static de.health.service.cetp.domain.eventservice.card.CardType.SMC_B;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

@SuppressWarnings("unused")
@RequestScoped
@Path("telematik")
public class Telematik extends AbstractResource {

    @Inject
    IKonnektorClient konnektorClient;

    @APIResponses({
        @APIResponse(responseCode = "200", description = "TelematikId string value"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Produces(TEXT_PLAIN)
    @Path("id")
    @Operation(summary = "Return telematikId for given SMC-B card")
    public Response get(
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)"
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = "iccsn", description = "SMC-B card ICCSN", required = true)
        @QueryParam("iccsn") String iccsn
    ) throws Exception {
        List<Card> cards = konnektorClient.getCards(userRuntimeConfig, SMC_B);
        String smcbHandle = cards.stream()
            .filter(c -> iccsn.equals(c.getIccsn()))
            .findAny()
            .map(Card::getCardHandle)
            .orElseThrow(() -> new CetpFault(String.format("Could not get SMC-B card for ICCSN: %s", iccsn)));
        return Response.ok(konnektorClient.getTelematikId(userRuntimeConfig, smcbHandle)).build();
    }
}
