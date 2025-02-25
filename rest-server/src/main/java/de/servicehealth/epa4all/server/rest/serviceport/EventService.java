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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;

@SuppressWarnings("unused")
@RequestScoped
@Path("event")
public class EventService extends AbstractResource {

    @Inject
    KonnektorClient konnektorClient;

    @APIResponses({
        @APIResponse(responseCode = "200", description = "Konnektor GetCardsResponse"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Produces(APPLICATION_XML)
    @Path("cards")
    @Operation(summary = "Return cards inserted into Konnektor")
    public GetCardsResponse cards(
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)",
            hidden = true
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(
            name = "cardType",
            description = "Type of the card to search, all types if skipped",
            example = "EGK/SMC-B/SMC-KT/etc"
        )
        @QueryParam("cardType") String cardType
    ) throws Exception {
        CardType ct = cardType == null ? null : CardType.valueOf(cardType);
        return konnektorClient.getCardsResponse(userRuntimeConfig, ct);
    }

    @APIResponses({
        @APIResponse(responseCode = "200", description = "Konnektor GetCardTerminalsResponse"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Produces(APPLICATION_XML)
    @Path("cardterminals")
    @Operation(summary = "Return card terminals attached to the Konnektor")
    public GetCardTerminalsResponse cardterminals(
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)",
            hidden = true
        )
        @QueryParam(X_KONNEKTOR) String konnektor
    ) throws Exception {
        return konnektorClient.getCardTerminalsResponse(userRuntimeConfig);
    }
}
