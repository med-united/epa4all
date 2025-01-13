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

    @GET
    @Produces(TEXT_PLAIN)
    @Path("id")
    public Response get(
        @QueryParam(X_KONNEKTOR) String konnektor,
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
