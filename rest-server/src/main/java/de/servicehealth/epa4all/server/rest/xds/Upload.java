package de.servicehealth.epa4all.server.rest.xds;

import de.servicehealth.epa4all.server.filetracker.upload.FileUpload;
import de.servicehealth.epa4all.server.rest.EpaContext;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.io.InputStream;
import java.util.UUID;

import static de.servicehealth.epa4all.server.rest.xds.XdsResource.XDS_DOCUMENT_PATH;
import static de.servicehealth.epa4all.xds.XDSUtils.isPdfCompliant;
import static de.servicehealth.epa4all.xds.XDSUtils.isXmlCompliant;
import static de.servicehealth.vau.VauClient.KVNR;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;

@SuppressWarnings("unused")
@RequestScoped
@Path(XDS_DOCUMENT_PATH)
public class Upload extends XdsResource {

    // Based on: https://github.com/gematik/api-ePA/blob/ePA-2.6/samples/ePA%201%20Beispielnachrichten%20PS%20-%20Konnektor/Requests/provideandregister.xml
    @POST
    @Consumes(MediaType.MEDIA_TYPE_WILDCARD)
    @Produces(MediaType.TEXT_PLAIN)
    @Path("upload")
    public String upload(
        @HeaderParam(CONTENT_TYPE) String contentType,
        @HeaderParam("Lang-Code") String languageCode,
        @HeaderParam("File-Name") String fileName,
        @QueryParam(X_KONNEKTOR) String konnektor,
        @QueryParam(KVNR) String kvnr,
        InputStream is
    ) throws Exception {
        EpaContext epaContext = prepareEpaContext(kvnr);
        if (fileName == null) {
            fileName = String.format("%s_%s.%s", kvnr, UUID.randomUUID(), getExtension(contentType));
        }
        String taskId = UUID.randomUUID().toString();
        FileUpload fileUpload = new FileUpload(
            epaContext,
            taskId,
            contentType,
            languageCode,
            telematikId,
            kvnr,
            fileName,
            null,
            is.readAllBytes()
        );
        eventFileUpload.fireAsync(fileUpload);
        return taskId;
    }

    private String getExtension(String contentType) {
        if (isXmlCompliant(contentType)) {
            return "xml";
        } else if (isPdfCompliant(contentType)) {
            return "pdf";
        } else {
            return "dat";
        }
    }
}
