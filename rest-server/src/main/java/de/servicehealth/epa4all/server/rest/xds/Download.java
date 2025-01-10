package de.servicehealth.epa4all.server.rest.xds;

import de.servicehealth.epa4all.server.filetracker.download.FileDownload;
import de.servicehealth.epa4all.server.rest.EpaContext;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotType1;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;

import java.util.Optional;
import java.util.UUID;

import static de.servicehealth.epa4all.server.rest.xds.XdsResource.XDS_DOCUMENT_PATH;
import static de.servicehealth.epa4all.xds.XDSUtils.isPdfCompliant;
import static de.servicehealth.epa4all.xds.XDSUtils.isXmlCompliant;
import static de.servicehealth.vau.VauClient.KVNR;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;

@SuppressWarnings("unused")
@RequestScoped
@Path(XDS_DOCUMENT_PATH)
public class Download extends XdsResource {

    @GET
    @Path("download/{uniqueId}")
    public RetrieveDocumentSetResponseType download(
        @PathParam("uniqueId") String uniqueId, // value from queryAllRemoteDocsInfo response
        @QueryParam(X_KONNEKTOR) String konnektor,
        @QueryParam(KVNR) String kvnr
    ) throws Exception {
        EpaContext epaContext = getEpaContext(kvnr);
        AdhocQueryResponse adhocQueryResponse = getAdhocQueryResponse(kvnr, epaContext);
        
        Optional<String> repositoryUniqueIdOpt = adhocQueryResponse.getRegistryObjectList().getIdentifiable()
            .stream()
            .filter(e -> {
                Optional<SlotType1> fileNameOpt = e.getValue().getSlot().stream().filter(s -> s.getName().equals("URI")).findFirst();
                return fileNameOpt.map(st -> st.getValueList().getValue().getFirst().contains(uniqueId)).isPresent();
            })
            .findFirst()
            .flatMap(e -> e.getValue().getSlot()
                .stream()
                .filter(s -> s.getName().equals("repositoryUniqueId"))
                .findFirst().map(st -> st.getValueList().getValue().getFirst())
            );

        String taskId = UUID.randomUUID().toString();
        String repositoryUniqueId = repositoryUniqueIdOpt.orElse("undefined");
        IDocumentManagementPortType documentManagementPortType = epaFileDownloader.getDocumentManagementPortType(taskId, epaContext);
        RetrieveDocumentSetRequestType requestType = xdsDocumentService.get().prepareRetrieveDocumentSetRequestType(
            uniqueId, repositoryUniqueId
        );
        RetrieveDocumentSetResponseType responseType = documentManagementPortType.documentRepositoryRetrieveDocumentSet(requestType);
        RegistryResponseType registryResponse = responseType.getRegistryResponse();
        boolean success = registryResponse.getStatus().contains("Success");
        if (success) {
            RetrieveDocumentSetResponseType.DocumentResponse documentResponse = responseType.getDocumentResponse().getFirst();
            String fileName = getFileName(uniqueId, documentResponse.getMimeType());
            FileDownload fileDownload = new FileDownload(epaContext, taskId, fileName, telematikId, kvnr, repositoryUniqueId);

            epaFileDownloader.handleDownloadResponse(fileDownload, documentResponse);
        }
        return responseType;
    }

    private String getFileName(String uniqueId, String mimeType) {
        String fileName = uniqueId;
        if (isXmlCompliant(mimeType)) {
            fileName = fileName + ".xml";
        } else if (isPdfCompliant(mimeType)) {
            fileName = fileName + ".pdf";
        }
        return fileName;
    }
}
