package de.servicehealth.epa4all.server.rest;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.servicehealth.epa4all.server.insurance.ReadVSDResponseEx;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Base64;

import static de.servicehealth.epa4all.server.smcb.VsdResponseFile.extractInsurantId;
import static de.servicehealth.epa4all.server.vsd.VsdService.buildSyntheticVSDResponse;

@RequestScoped
@Path("vsd")
public class Vsd extends AbstractResource {

    @POST
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    @Path("pnw")
    public Response proxy(
        @QueryParam("{x-konnektor : ([0-9a-zA-Z\\-\\.]+)?}") String konnektor,
        byte[] base64EncodedBody
    ) throws Exception {
        folderService.applyTelematikPath(telematikId);

        byte[] pruefungsnachweis = Base64.getDecoder().decode(base64EncodedBody);
        ReadVSDResponse readVSDResponse = buildSyntheticVSDResponse(null, pruefungsnachweis);
        String insurantId = extractInsurantId(readVSDResponse, true);
        readVSDResponseExEvent.fire(new ReadVSDResponseEx(telematikId, insurantId, readVSDResponse));

        // call to getLocalInsuranceData *AFTER* we set it by synthetic ReadVSDResponse
        prepareEpaContext(insurantId);

        return Response.status(Response.Status.CREATED).build();
    }
}
