package de.servicehealth.epa4all.server.rest.xds;

import de.servicehealth.epa4all.server.rest.EpaContext;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.xml.bind.JAXBElement;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.IdentifiableType;
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
public class DownloadAll extends XdsResource {

    @APIResponses({
        @APIResponse(responseCode = "200", description = "Task uuid array"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Path("downloadAll")
    @Operation(summary = "Download all documents of the patient from the XDS registry")
    public List<String> downloadAll(
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)"
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = KVNR, description = "Patient KVNR", required = true)
        @QueryParam(KVNR) String kvnr
    ) throws Exception {
        EpaContext epaContext = prepareEpaContext(kvnr);
        AdhocQueryResponse adhocQueryResponse = getAdhocQueryResponse(kvnr, epaContext);
        List<JAXBElement<? extends IdentifiableType>> jaxbElements = adhocQueryResponse.getRegistryObjectList().getIdentifiable();
        return bulkTransfer.downloadInsurantFiles(
            epaContext, telematikId, kvnr, jaxbElements
        );
    }
}
