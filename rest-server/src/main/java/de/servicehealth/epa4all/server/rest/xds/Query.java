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

    @GET
    @Path("query")
    public AdhocQueryResponse queryAllRemoteDocsInfo(
        @QueryParam(X_KONNEKTOR) String konnektor,
        @QueryParam(KVNR) String kvnr
    ) throws Exception {
        EpaContext epaContext = getEpaContext(kvnr);
        return getAdhocQueryResponse(kvnr, epaContext);
    }

    @GET
    @Path("task/{taskId}")
    public RegistryResponseType checkTask(@PathParam(TASK_ID) String taskId) {
        return epaFileDownloader.getResult(taskId);
    }
}
