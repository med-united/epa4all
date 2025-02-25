package de.servicehealth.epa4all.server.rest.xds;

import de.servicehealth.epa4all.server.rest.EpaContext;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import static de.servicehealth.epa4all.server.rest.xds.XdsResource.XDS_DOCUMENT_PATH;
import static de.servicehealth.vau.VauClient.KVNR;
import static de.servicehealth.vau.VauClient.TASK_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;

@SuppressWarnings("unused")
@RequestScoped
@Path(XDS_DOCUMENT_PATH)
@Produces(APPLICATION_XML)
public class Query extends XdsResource {

    @APIResponses({
        @APIResponse(responseCode = "200", description = "XDS AdhocQueryResponse"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Path("query")
    @Operation(summary = "Query information about patient's remote documents")
    public AdhocQueryResponse queryAllRemoteDocsInfo(
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
        return getAdhocQueryResponse(kvnr, epaContext);
    }

    @APIResponses({
        @APIResponse(responseCode = "200", description = "XDS RegistryResponseType"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Path("task/{taskId}")
    @Operation(summary = "Query information about task completion status")
    public RegistryResponseType checkTask(
        @Parameter(name = TASK_ID, description = "Task uuid", required = true)
        @PathParam(TASK_ID) String taskId
    ) {
        return epaFileDownloader.getResult(taskId);
    }
}
