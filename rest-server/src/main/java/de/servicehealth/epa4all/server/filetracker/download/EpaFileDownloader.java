package de.servicehealth.epa4all.server.filetracker.download;

import de.servicehealth.epa4all.server.filetracker.EpaFileTracker;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import de.servicehealth.epa4all.xds.structure.ExtrinsicContext;
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
            uniqueId, fileAction.getExtrinsicContext().repositoryUniqueId()
        );
        RetrieveDocumentSetResponseType responseType = documentPortType.documentRepositoryRetrieveDocumentSet(requestType);
        RegistryResponseType registryResponse = responseType.getRegistryResponse();
        boolean success = registryResponse.getStatus().contains("Success");
        if (success) {
            handleDownloadResponse(fileAction, responseType.getDocumentResponse().getFirst(), true);
        }
        return registryResponse;
    }

    public void handleDownloadResponse(
        FileDownload fileDownload,
        RetrieveDocumentSetResponseType.DocumentResponse documentResponse,
        boolean validate
    ) throws Exception {
        String taskId = fileDownload.getTaskId();
        ExtrinsicContext extrinsicContext = fileDownload.getExtrinsicContext();
        if (validate) {
            validateDownloadResponse(taskId, extrinsicContext, documentResponse);
        }

        StructureDefinition structureDefinition = structureDefinitionService.getStructureDefinition(taskId, null, extrinsicContext);
        String folderCode = getFolderCode(structureDefinition);
        String telematikId = fileDownload.getTelematikId();
        String insurantId = fileDownload.getKvnr();
        folderService.storeNewFile(fileDownload.getFileName(), folderCode, telematikId, insurantId, documentResponse.getDocument());
        log.info(String.format("[%s/%s] downloaded successfully", folderCode, fileDownload.getFileName()));
    }

    private void validateDownloadResponse(
        String taskId,
        ExtrinsicContext extrinsicContext,
        RetrieveDocumentSetResponseType.DocumentResponse documentResponse
    ) {
        String documentUniqueId = documentResponse.getDocumentUniqueId();
        String extrinsicUniqueId = extrinsicContext.uniqueId();
        if (!extrinsicUniqueId.contains(documentUniqueId)) {
            log.warn(String.format("[taskId=%s] uniqueId mismatch: %s %s", taskId, extrinsicUniqueId, documentUniqueId));
        }

        String sourceMimeType = extrinsicContext.mimeType();
        String targetMimeType = documentResponse.getMimeType();
        if (!sourceMimeType.equalsIgnoreCase(targetMimeType)) {
            log.warn(String.format("[taskId=%s] mimeType mismatch: %s %s", taskId, sourceMimeType, targetMimeType));
        }
    }
}
