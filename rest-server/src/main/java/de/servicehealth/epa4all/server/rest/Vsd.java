package de.servicehealth.epa4all.server.rest;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.servicehealth.epa4all.server.rest.exception.EpaClientError;
import de.servicehealth.epa4all.server.vsd.VsdService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Base64;

import static de.servicehealth.epa4all.server.vsd.VsdResponseFile.extractInsurantId;
import static de.servicehealth.epa4all.server.vsd.VsdService.buildSyntheticVSDResponse;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;

@SuppressWarnings("unused")
@RequestScoped
@Path("vsd")
public class Vsd extends AbstractResource {

    @Inject
    VsdService vsdService;

    @Deprecated
    @POST
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    @Path("pnw")
    public Response proxy(
        @QueryParam(X_KONNEKTOR) String konnektor,
        byte[] base64EncodedBody
    ) throws Exception {
        byte[] pruefungsnachweis = Base64.getDecoder().decode(base64EncodedBody);
        ReadVSDResponse readVSDResponse = buildSyntheticVSDResponse(null, pruefungsnachweis);
        String insurantId = extractInsurantId(readVSDResponse, null);
        if (insurantId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new EpaClientError("Unable to get insurantId"))
                .build();
        } else {
            vsdService.saveVsdFile(telematikId, insurantId, readVSDResponse);
            prepareEpaContext(insurantId);
            return Response.status(Response.Status.CREATED).build();
        }
    }
}
