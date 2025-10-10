package de.servicehealth.epa4all.server.rest.xds;

import de.servicehealth.epa4all.server.filetracker.change.FileChange;
import de.servicehealth.epa4all.server.rest.EpaContext;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import oasis.names.tc.ebxml_regrep.xsd.lcm._3.SubmitObjectsRequest;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.io.InputStream;
import java.util.UUID;

import static de.servicehealth.epa4all.server.filetracker.upload.soap.RawSoapUtils.deserializeSubmitElement;
import static de.servicehealth.epa4all.server.rest.xds.XdsResource.XDS_DOCUMENT_PATH;
import static de.servicehealth.vau.VauClient.KVNR;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static jakarta.ws.rs.core.MediaType.MEDIA_TYPE_WILDCARD;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.eclipse.microprofile.openapi.annotations.enums.ParameterIn.HEADER;

@SuppressWarnings("unused")
@RequestScoped
@Path(XDS_DOCUMENT_PATH)
public class Change extends XdsResource {

    @APIResponses({
        @APIResponse(responseCode = "200", description = "Task uuid"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @POST
    @Consumes(MEDIA_TYPE_WILDCARD)
    @Produces(TEXT_PLAIN)
    @Path("change/raw")
    @Operation(summary = "Change document metadata in the XDS registry")
    public String change(
        @Parameter(
            name = "File-Name",
            description = "Original filename of the document",
            example = "medical-report.pdf",
            in = HEADER
        )
        @HeaderParam("File-Name") String fileName,
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)"
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = KVNR, description = "Patient KVNR", required = true)
        @QueryParam(KVNR) String kvnr,
        @Parameter(description = "ns4:SubmitObjectsRequest XML element to submit to the XDS registry")
        InputStream is
    ) throws Exception {
        EpaContext epaContext = prepareEpaContext(kvnr);

        String xmlElement = new String(is.readAllBytes(), ISO_8859_1);
        SubmitObjectsRequest request = deserializeSubmitElement(xmlElement);

        String taskId = UUID.randomUUID().toString();
        FileChange fileChange = new FileChange(
            epaContext,
            taskId,
            fileName,
            telematikId,
            kvnr,
            request
        );
        fileActionEvent.fireAsync(fileChange);
        return taskId;
    }
}