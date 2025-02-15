package de.servicehealth.epa4all.server.rest.serviceport;

import de.gematik.ws.conn.eventservice.v7.GetCardTerminalsResponse;
import de.gematik.ws.conn.eventservice.v7.GetCardsResponse;
import de.health.service.cetp.domain.eventservice.card.CardType;
import de.servicehealth.epa4all.server.cetp.KonnektorClient;
import de.servicehealth.epa4all.server.rest.AbstractResource;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;

import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;

@SuppressWarnings("unused")
@RequestScoped
@Path("event")
public class EventService extends AbstractResource {

    @Inject
    KonnektorClient konnektorClient;

    @GET
    @Produces(APPLICATION_XML)
    @Path("cards")
    public GetCardsResponse cards(
        @QueryParam(X_KONNEKTOR) String konnektor,
        @QueryParam("cardType") String cardType
    ) throws Exception {
        CardType ct = cardType == null ? null : CardType.valueOf(cardType);
        return konnektorClient.getCardsResponse(userRuntimeConfig, ct);
    }

    @GET
    @Produces(APPLICATION_XML)
    @Path("cardterminals")
    public GetCardTerminalsResponse cardterminals(
            @QueryParam(X_KONNEKTOR) String konnektor
    ) throws Exception {
        return konnektorClient.getCardTerminalsResponse(userRuntimeConfig);
    }
}
