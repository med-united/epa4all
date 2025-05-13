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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.io.InputStream;
import java.util.UUID;

import static de.servicehealth.epa4all.server.rest.xds.XdsResource.XDS_DOCUMENT_PATH;
import static de.servicehealth.epa4all.xds.XDSUtils.isPdfCompliant;
import static de.servicehealth.epa4all.xds.XDSUtils.isXmlCompliant;
import static de.servicehealth.vau.VauClient.KVNR;
import static de.servicehealth.vau.VauClient.UPLOAD_CONTENT_TYPE;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;

@SuppressWarnings("unused")
@RequestScoped
@Path(XDS_DOCUMENT_PATH)
public class Upload extends XdsResource {

    // Based on: https://github.com/gematik/api-ePA/blob/ePA-2.6/samples/ePA%201%20Beispielnachrichten%20PS%20-%20Konnektor/Requests/provideandregister.xml
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Task uuid"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @POST
    @Consumes(MediaType.MEDIA_TYPE_WILDCARD)
    @Produces(MediaType.TEXT_PLAIN)
    @Path("upload")
    @Operation(summary = "Upload single document XML/PDF/etc to the XDS registry")
    public String upload(
        @HeaderParam(CONTENT_TYPE) String contentType,
        @HeaderParam("Lang-Code") String languageCode,
        @HeaderParam("File-Name") String fileName,
        @HeaderParam("Title") String title,
        @HeaderParam("Author-Lanr") String authorLanr,
        @HeaderParam("Author-FirstName") String authorFirstName,
        @HeaderParam("Author-LastName") String authorLastName,
        @HeaderParam("Author-Title") String authorTitle,
        @HeaderParam("Praxis") String praxis,
        @HeaderParam("Fachrichtung") String practiceSetting,
        @HeaderParam("Information") String information,
        @HeaderParam("Information2") String information2,
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)"
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = KVNR, description = "Patient KVNR", required = true)
        @QueryParam(KVNR) String kvnr,
        @Parameter(name = "ig", description = "IG schema name")
        @QueryParam("ig") String ig,
        @Parameter(description = "Document to submit to the XDS registry", example = "xml/pdf")
        InputStream is
    ) throws Exception {
        EpaContext epaContext = prepareEpaContext(kvnr);
        epaContext.getXHeaders().put(UPLOAD_CONTENT_TYPE, contentType);
        if (fileName == null) {
            fileName = String.format("%s_%s.%s", kvnr, UUID.randomUUID(), getExtension(contentType));
        }
        String taskId = UUID.randomUUID().toString();
        FileUpload fileUpload = new FileUpload(
            epaContext,
            ig,
            taskId,
            contentType,
            languageCode,
            telematikId,
            kvnr,
            fileName,
            title,
            authorLanr,
            authorFirstName,
            authorLastName,
            authorTitle, 
            praxis,
            practiceSetting,
            information,
            information2,
            "other",
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