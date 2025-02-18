package de.servicehealth.epa4all.server.filetracker;

import de.service.health.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.server.epa.EpaCallGuard;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.server.xdsdocument.XDSDocumentService;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import de.servicehealth.epa4all.xds.structure.StructureDefinitionService;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryError;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryErrorList;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class EpaFileTracker<T extends FileAction> {

    private static final Logger log = LoggerFactory.getLogger(EpaFileTracker.class.getName());

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS");
    private static final Map<String, RegistryResponseType> resultsMap = new ConcurrentHashMap<>();

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    ManagedExecutor filesTransferExecutor;

    @Inject
    EpaCallGuard epaCallGuard;

    @Inject
    protected FolderService folderService;

    @Inject
    EpaMultiService epaMultiService;

    @Inject
    protected Instance<XDSDocumentService> xdsDocumentService;

    @Inject
    protected StructureDefinitionService structureDefinitionService;


    public RegistryResponseType getResult(String taskId) {
        RegistryResponseType responseType = resultsMap.get(taskId);
        if (responseType == null) {
            return null;
        }
        if (!responseType.getStatus().contains("InProgress")) {
            resultsMap.remove(taskId);
        }
        return responseType;
    }

    public void onTransfer(@ObservesAsync T fileAction) {
    	// this is already in an own thread to technically no need to start an own
        filesTransferExecutor.submit(() -> transferFile(fileAction));
    }

    private void transferFile(T fileAction) {
        String taskId = fileAction.getTaskId();
        if (resultsMap.containsKey(taskId)) {
            return;
        }
        EpaContext epaContext = fileAction.getEpaContext();
        resultsMap.computeIfAbsent(taskId, (k) -> prepareInProgressResponse(taskId));
        try {
            String insurantId = epaContext.getInsurantId();
            Map<String, String> xHeaders = epaContext.getXHeaders();
            IDocumentManagementPortType documentManagementPortType = epaMultiService
                .findEpaAPI(insurantId)
                .getDocumentManagementPortType(taskId, xHeaders);
            RegistryResponseType responseType = epaCallGuard.callAndRetry(
                epaContext.getBackend(),
                () -> handleTransfer(fileAction, documentManagementPortType)
            );
            responseType.setRequestId(taskId);
            resultsMap.computeIfPresent(taskId, (k, prev) -> responseType);
        } catch (Exception e) {
            String s = fileAction.isUpload() ? "uploading" : "downloading";
            log.error(String.format("[%s] Error while %s %s", taskId, s, fileAction), e);

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
}
