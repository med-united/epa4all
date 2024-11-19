package de.servicehealth.epa4all.server.filetracker;

import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.server.xdsdocument.XDSDocumentService;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import de.servicehealth.epa4all.xds.structure.StructureDefinitionService;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.xml.ws.BindingProvider;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryError;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryErrorList;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.jboss.logmanager.Level;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

import static de.health.service.cetp.utils.Utils.saveDataToFile;

public abstract class EpaFileTracker<T extends FileAction> {

    private static final Logger log = Logger.getLogger(EpaFileTracker.class.getName());
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ThreadFactory threadFactory = Thread.ofVirtual().name("upload-file-", 0).factory();
    private static final ExecutorService filesTransferExecutor = Executors.newThreadPerTaskExecutor(threadFactory);
    private static final Map<String, RegistryResponseType> resultsMap = new ConcurrentHashMap<>();

    @Inject
    FolderService folderService;

    @Inject
    MultiEpaService multiEpaService;

    @Inject
    protected Instance<XDSDocumentService> xdsDocumentService;

    @Inject
    protected StructureDefinitionService structureDefinitionService;


    public RegistryResponseType getResult(String taskId) {
        return resultsMap.remove(taskId);
    }

    public void onTransfer(@ObservesAsync T fileAction) {
        filesTransferExecutor.submit(() -> transferFile(fileAction));
    }

    private void transferFile(T fileAction) {
        String taskId = fileAction.getTaskId();
        if (resultsMap.containsKey(taskId)) {
            return;
        }
        EpaContext epaContext = fileAction.getEpaContext();
        resultsMap.computeIfAbsent(taskId, (k) -> prepareInProgressResponse(taskId));
        IDocumentManagementPortType documentPortType = getDocumentManagementPortType(epaContext);
        try {
            RegistryResponseType responseType = handleTransfer(fileAction, documentPortType);
            resultsMap.computeIfPresent(taskId, (k, prev) -> responseType);
        } catch (Exception e) {
            String s = fileAction.isUpload() ? "uploading" : "downloading";
            log.log(Level.SEVERE, String.format("[%s] Error while %s %s", taskId, s, fileAction), e);

            resultsMap.computeIfPresent(taskId, (k, prev) -> {
                String startedAt = prev.getStatus().split(" ")[1];
                return prepareFailedResponse(taskId, startedAt, e.getMessage());
            });
        }
    }

    protected abstract RegistryResponseType handleTransfer(T fileAction, IDocumentManagementPortType documentPortType) throws Exception;

    @SuppressWarnings("unchecked")
    protected String getFolderCode(StructureDefinition structureDefinition) {
        Map<String, String> map = (Map<String, String>) structureDefinition.getMetadata().getValue();
        return map.get("code");
    }

    protected void storeNewFile(
        String fileName,
        String folderCode,
        String telematikId,
        String insurantId,
        byte[] documentBytes
    ) throws Exception {
        if (folderService.appendChecksumFor(telematikId, insurantId, documentBytes)) {
            File medFolder = getMedFolder(telematikId, insurantId, folderCode);
            File file = new File(medFolder, fileName);
            if (!file.exists()) {
                saveDataToFile(documentBytes, file);
            }
        }
    }

    public IDocumentManagementPortType getDocumentManagementPortType(EpaContext epaContext) {
        EpaAPI epaAPI = multiEpaService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());
        IDocumentManagementPortType documentManagementPortType = epaAPI.getDocumentManagementPortType();
        ((BindingProvider) documentManagementPortType).getRequestContext().putAll(epaContext.getRuntimeAttributes());
        return documentManagementPortType;
    }

    private RegistryResponseType prepareInProgressResponse(String taskId) {
        RegistryResponseType registryResponse = new RegistryResponseType();
        String startedAt = LocalDateTime.now().format(FORMATTER);
        registryResponse.setRequestId(taskId);
        registryResponse.setStatus(String.format("InProgress, startedAt=%s", startedAt));
        return registryResponse;
    }

    private RegistryResponseType prepareFailedResponse(String taskId, String startedAt, String errorMessage) {
        RegistryResponseType responseType = new RegistryResponseType();
        RegistryError registryError = new RegistryError();
        registryError.setErrorCode("Failed");
        registryError.setValue(errorMessage);
        String failedAt = LocalDateTime.now().format(FORMATTER);
        responseType.setStatus(String.format("Failed, %s failedAt=%s", startedAt, failedAt));
        responseType.setRequestId(taskId);
        RegistryErrorList registryErrorList = new RegistryErrorList();
        registryErrorList.getRegistryError().add(registryError);
        responseType.setRegistryErrorList(registryErrorList);
        return responseType;
    }

    private File getMedFolder(String telematikId, String insurantId, String folderCode) {
        File medFolder = folderService.getInsurantMedFolder(telematikId, insurantId, folderCode);
        if (medFolder == null || !medFolder.exists()) {
            String insurantFolderPath = folderService.getInsurantFolder(telematikId, insurantId).getAbsolutePath();
            File otherFolder = folderService.getInsurantMedFolder(telematikId, insurantId, "other");
            medFolder = folderService.createFolder(insurantFolderPath + File.separator + folderCode, otherFolder);
        }
        return medFolder;
    }
}
