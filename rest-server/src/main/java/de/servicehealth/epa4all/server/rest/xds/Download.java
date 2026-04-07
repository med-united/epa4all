package de.servicehealth.epa4all.server.rest.xds;

import de.servicehealth.epa4all.server.filetracker.download.FileDownload;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.xds.structure.ExtrinsicContext;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.util.UUID;

import static de.servicehealth.epa4all.server.rest.xds.XdsResource.XDS_DOCUMENT_PATH;
import static de.servicehealth.epa4all.xds.structure.ExtrinsicHelper.buildExtrinsicContext;
import static de.servicehealth.epa4all.xds.structure.ExtrinsicHelper.getExtrinsicObject;
import static de.servicehealth.vau.VauClient.KVNR;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;

@SuppressWarnings("unused")
@RequestScoped
@Path(XDS_DOCUMENT_PATH)
public class Download extends XdsResource {

    @APIResponses({
        @APIResponse(responseCode = "200", description = "ePA XDS RetrieveDocumentSetResponseType"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @GET
    @Path("download/{uniqueId}")
    @Operation(summary = "Download single document from the XDS registry")
    public RetrieveDocumentSetResponseType download(
        @Parameter(
            name = "uniqueId",
            description = "Document unique identifier from query response",
            example = "2.259532960832105435533.4853516939077959994"
        )
        @PathParam("uniqueId") String uniqueId,
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
        ExtrinsicObjectType extrinsicObject = getExtrinsicObject(adhocQueryResponse, uniqueId);
        ExtrinsicContext extrinsicContext = buildExtrinsicContext(extrinsicObject);

        String taskId = UUID.randomUUID().toString();

        IDocumentManagementPortType documentManagementPortType = epaMultiService
            .findEpaAPI(epaContext.getInsurantId())
            .getDocumentManagementPortType(taskId, epaContext.getXHeaders());

        RetrieveDocumentSetRequestType requestType = xdsDocumentService.get()
            .prepareRetrieveDocumentSetRequestType(uniqueId, extrinsicContext.repositoryUniqueId());

        RetrieveDocumentSetResponseType responseType = documentManagementPortType.documentRepositoryRetrieveDocumentSet(requestType);
        boolean success = responseType.getRegistryResponse().getStatus().contains("Success");
        if (success) {
            RetrieveDocumentSetResponseType.DocumentResponse documentResponse = responseType.getDocumentResponse().getFirst();
            String fileName = extrinsicContext.uri();
            FileDownload fileDownload = new FileDownload(taskId, telematikId, kvnr, fileName, epaContext, extrinsicContext);
            epaFileDownloader.handleDownloadResponse(fileDownload, documentResponse, true);
        }
        return responseType;
    }
}
