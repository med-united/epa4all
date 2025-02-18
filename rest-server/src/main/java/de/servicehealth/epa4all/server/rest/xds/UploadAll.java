package de.servicehealth.epa4all.server.rest.xds;

import de.servicehealth.epa4all.server.rest.EpaContext;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

import static de.servicehealth.epa4all.server.rest.xds.XdsResource.XDS_DOCUMENT_PATH;
import static de.servicehealth.vau.VauClient.KVNR;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;

@SuppressWarnings("unused")
@RequestScoped
@Path(XDS_DOCUMENT_PATH)
public class UploadAll extends XdsResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("uploadAll")
    public String uploadAll(
        @HeaderParam("Lang-Code") String languageCode,
        @QueryParam(X_KONNEKTOR) String konnektor,
        @QueryParam(KVNR) String kvnr
    ) throws Exception {
        EpaContext epaContext = prepareEpaContext(kvnr);
        List<String> tasksIds = bulkTransfer.uploadInsurantFiles(epaContext, telematikId, kvnr, languageCode);
        return String.join("\n", tasksIds);
    }
}
