package de.servicehealth.epa4all.server.filetracker.download;

import de.servicehealth.epa4all.server.filetracker.EpaFileTracker;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import jakarta.enterprise.context.RequestScoped;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;

import java.util.logging.Logger;

@RequestScoped
public class EpaFileDownloader extends EpaFileTracker<FileDownload> {

    private static final Logger log = Logger.getLogger(EpaFileDownloader.class.getName());

    @Override
    protected RegistryResponseType handleTransfer(FileDownload fileAction, IDocumentManagementPortType documentPortType) throws Exception {
        String taskId = fileAction.getTaskId();
        String uniqueId = fileAction.getFileName().replace(".xml", "").replace(".pdf", "");
        RetrieveDocumentSetRequestType requestType = xdsDocumentService.get().prepareRetrieveDocumentSetRequestType(
            uniqueId, fileAction.getRepositoryUniqueId()
        );
        RetrieveDocumentSetResponseType responseType = documentPortType.documentRepositoryRetrieveDocumentSet(requestType);
        handleDownloadResponse(taskId, fileAction, responseType);
        return responseType.getRegistryResponse();
    }

    public void handleDownloadResponse(
        String taskId,
        FileDownload fileDownload,
        RetrieveDocumentSetResponseType response
    ) throws Exception {
        RegistryResponseType registryResponse = response.getRegistryResponse();
        boolean success = registryResponse.getStatus().contains("Success");
        if (success) {
            RetrieveDocumentSetResponseType.DocumentResponse documentResponse = response.getDocumentResponse().getFirst();
            byte[] documentBytes = documentResponse.getDocument();
            String mimeType = documentResponse.getMimeType();
            String fileName = documentResponse.getDocumentUniqueId();
            if (!fileName.equalsIgnoreCase(fileDownload.getFileName())) {
                log.warning(String.format("[%s] file names mismatch: %s %s", taskId, fileName, fileDownload.getFileName()));
            }

            StructureDefinition structureDefinition = structureDefinitionService.getStructureDefinition(mimeType, documentBytes);
            String telematikId = fileDownload.getTelematikId();
            String insurantId = fileDownload.getKvnr();
            String folderCode = getFolderCode(structureDefinition);

            storeNewFile(fileDownload.getFileName(), folderCode, telematikId, insurantId, documentBytes);
            log.info(String.format("[%s/%s] downloaded successfully", folderCode, fileDownload.getFileName()));
        } 
    }
}
