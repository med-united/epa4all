package de.servicehealth.epa4all.server.filetracker;

import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.xml.ws.BindingProvider;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryError;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.jboss.logmanager.Level;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;

import static de.health.service.cetp.utils.Utils.saveDataToFile;

@ApplicationScoped
public class EpaFileTracker {

    private static final Logger log = Logger.getLogger(EpaFileTracker.class.getName());

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<String, RegistryResponseType> uploadResultsMap = new ConcurrentHashMap<>();

    private static final ThreadFactory threadFactory = Thread.ofVirtual().name("upload-file-", 0).factory();
    private static final ExecutorService uploadFilesExecutor = Executors.newThreadPerTaskExecutor(threadFactory);

    private final FolderService folderService;
    private final MultiEpaService multiEpaService;

    @Inject
    public EpaFileTracker(
        FolderService folderService,
        MultiEpaService multiEpaService
    ) {
        this.folderService = folderService;
        this.multiEpaService = multiEpaService;
    }

    void onStart(@Observes StartupEvent ev) {
    }

    public void onUpload(@ObservesAsync FileUpload fileUpload) {
        uploadFilesExecutor.submit(() -> uploadFile(fileUpload));
    }

    private void uploadFile(FileUpload fileUpload) {
        if (uploadResultsMap.containsKey(fileUpload.getTaskId())) {
            return;
        }
        uploadResultsMap.computeIfAbsent(fileUpload.getTaskId(), (k) -> {
            RegistryResponseType responseType = new RegistryResponseType();
            responseType.setRequestId(fileUpload.getTaskId());
            responseType.setStatus(String.format("InProgress, startedAt=%s", LocalDateTime.now().format(FORMATTER)));
            return responseType;
        });

        EpaContext epaContext = fileUpload.getEpaContext();
        
        EpaAPI epaAPI = multiEpaService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());
        IDocumentManagementPortType documentManagementPortType = epaAPI.getDocumentManagementPortType();
        ((BindingProvider) documentManagementPortType).getRequestContext().putAll(epaContext.getRuntimeAttributes());

        try {
            String fileName = fileUpload.getFileName();
            String telematikId = fileUpload.getTelematikId();
            String insurantId = fileUpload.getKvnr();
            byte[] documentBytes = fileUpload.getDocumentBytes();

            ProvideAndRegisterDocumentSetRequestType request = fileUpload.getRequest();
            StructureDefinition structureDefinition = fileUpload.getStructureDefinition();

            RegistryResponseType response = documentManagementPortType.documentRepositoryProvideAndRegisterDocumentSetB(request);
            List<RegistryError> registryError = response.getRegistryErrorList().getRegistryError();
            if (registryError.isEmpty()) {

                // NEW FILE: get fileName         | EXISTING FILE: existing fileName
                // NEW FILE: select webdav folder | EXISTING FILE: existing folder
                // NEW FILE: calculate checksum   | EXISTING FILE: check record in file and proceed if no record is present
                // NEW FILE: save                 | EXISTING FILE: no action
                // sync checksum file

                if (folderService.appendChecksumFor(telematikId, insurantId, documentBytes)) {
                    File medFolder = getMedFolder(structureDefinition, telematikId, insurantId);
                    File file = new File(medFolder, fileName);
                    saveDataToFile(documentBytes, file);
                }
            } else {
                log.log(Level.SEVERE, String.format("File upload failed: %s", registryError.getFirst().getValue()));
            }

            uploadResultsMap.computeIfPresent(fileUpload.getTaskId(), (k, prev) -> response);
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Error while uploading %s", fileUpload));

            uploadResultsMap.computeIfPresent(fileUpload.getTaskId(), (k, prev) -> {
                String startedAt = prev.getStatus().split(" ")[1];

                RegistryResponseType responseType = new RegistryResponseType();
                RegistryError registryError = new RegistryError();
                registryError.setErrorCode("Failed");
                registryError.setValue(e.getMessage());
                responseType.setStatus(String.format("Failed, %s failedAt=%s", startedAt, LocalDateTime.now().format(FORMATTER)));
                responseType.setRequestId(fileUpload.getTaskId());
                responseType.getRegistryErrorList().getRegistryError().add(registryError);
                return responseType;
            });
        }
    }

    @SuppressWarnings("unchecked")
    private File getMedFolder(
        StructureDefinition structureDefinition,
        String telematikId,
        String insurantId
    ) {
        Map<String, String> map = (Map<String, String>) structureDefinition.getMetadata().getValue();
        String folderName = map.get("code");
        File medFolder = folderService.getInsurantMedFolder(telematikId, insurantId, folderName);
        if (medFolder == null || !medFolder.exists()) {
            String insurantFolderPath = folderService.getInsurantFolder(telematikId, insurantId).getAbsolutePath();
            File otherFolder = folderService.getInsurantMedFolder(telematikId, insurantId, "other");
            medFolder = folderService.createFolder(insurantFolderPath + File.separator + folderName, otherFolder);
        }
        return medFolder;
    }

    public RegistryResponseType getResult(String taskUuid) {
        return uploadResultsMap.get(taskUuid);
    }
}
