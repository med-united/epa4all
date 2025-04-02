package de.servicehealth.epa4all.server.filetracker.download;

import de.servicehealth.epa4all.server.filetracker.EpaFileTracker;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import jakarta.enterprise.context.ApplicationScoped;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class EpaFileDownloader extends EpaFileTracker<FileDownload> {

    private static final Logger log = LoggerFactory.getLogger(EpaFileDownloader.class.getName());

    @Override
    protected RegistryResponseType handleTransfer(
        FileDownload fileAction,
        IDocumentManagementPortType documentPortType
    ) throws Exception {
        String uniqueId = fileAction.getFileName().replace(".xml", "").replace(".pdf", "");
        RetrieveDocumentSetRequestType requestType = xdsDocumentService.get().prepareRetrieveDocumentSetRequestType(
            uniqueId, fileAction.getRepositoryUniqueId()
        );
        RetrieveDocumentSetResponseType responseType = documentPortType.documentRepositoryRetrieveDocumentSet(requestType);
        RegistryResponseType registryResponse = responseType.getRegistryResponse();
        boolean success = registryResponse.getStatus().contains("Success");
        if (success) {
            handleDownloadResponse(fileAction, responseType.getDocumentResponse().getFirst());
        }
        return registryResponse;
    }

    public void handleDownloadResponse(
        FileDownload fileDownload,
        RetrieveDocumentSetResponseType.DocumentResponse documentResponse
    ) throws Exception {
        String taskId = fileDownload.getTaskId();
        byte[] documentBytes = documentResponse.getDocument();
        String mimeType = documentResponse.getMimeType();
        String fileName = documentResponse.getDocumentUniqueId();
        if (!fileDownload.getFileName().contains(fileName)) {
            log.warn(String.format("[taskId=%s] file names mismatch: %s %s", taskId, fileName, fileDownload.getFileName()));
        }

        String telematikId = fileDownload.getTelematikId();
        String insurantId = fileDownload.getKvnr();
        StructureDefinition structureDefinition = structureDefinitionService.getStructureDefinition(null, mimeType, documentBytes);
        String folderCode = "other";
        if(structureDefinition == null) {
        	log.warn(String.format("No structureDefinition for MIME-Type:%s was found; falling back to folderCode='%s'", mimeType, folderCode));
        } else {
        	folderCode = getFolderCode(structureDefinition);
        }

        folderService.storeNewFile(fileDownload.getFileName(), folderCode, telematikId, insurantId, documentBytes);
        log.info(String.format("[%s/%s] downloaded successfully", folderCode, fileDownload.getFileName()));
    }
}
