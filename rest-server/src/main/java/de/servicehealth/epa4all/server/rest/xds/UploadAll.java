package de.servicehealth.epa4all.server.rest.xds;

import de.servicehealth.epa4all.server.rest.EpaContext;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.List;

import static de.servicehealth.epa4all.server.rest.xds.XdsResource.XDS_DOCUMENT_PATH;
import static de.servicehealth.vau.VauClient.KVNR;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;

@SuppressWarnings("unused")
@RequestScoped
@Path(XDS_DOCUMENT_PATH)
public class UploadAll extends XdsResource {

    @APIResponses({
        @APIResponse(responseCode = "200", description = "Task uuid array"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("uploadAll")
    @Operation(summary = "Upload all documents of the patient from webdav KVNR medication folders to the XDS registry")
    public List<String> uploadAll(
        @HeaderParam("Lang-Code") String languageCode,
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)",
            hidden = true
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = KVNR, description = "Patient KVNR", required = true)
        @QueryParam(KVNR) String kvnr
    ) throws Exception {
        EpaContext epaContext = prepareEpaContext(kvnr);
        return bulkTransfer.uploadInsurantFiles(epaContext, telematikId, kvnr, languageCode);
    }
}
