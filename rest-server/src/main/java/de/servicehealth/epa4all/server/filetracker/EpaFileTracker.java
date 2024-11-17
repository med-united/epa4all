package de.servicehealth.epa4all.server.filetracker;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.server.xdsdocument.XDSDocumentService;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.xml.ws.BindingProvider;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryError;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryErrorList;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.apache.commons.lang3.tuple.Pair;
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
    private final Instance<XDSDocumentService> xdsDocumentService;

    @Inject
    public EpaFileTracker(
        FolderService folderService,
        MultiEpaService multiEpaService,
        Instance<XDSDocumentService> xdsDocumentService
    ) {
        this.folderService = folderService;
        this.multiEpaService = multiEpaService;
        this.xdsDocumentService = xdsDocumentService;
    }

    void onStart(@Observes StartupEvent ev) {
    }

    public void onUpload(@ObservesAsync FileUpload fileUpload) {
        uploadFilesExecutor.submit(() -> uploadFile(fileUpload));
    }

    private void uploadFile(FileUpload fileUpload) {
        String taskId = fileUpload.getTaskId();
        if (uploadResultsMap.containsKey(taskId)) {
            log.warning(String.format("Already in progress %s", fileUpload));
            return;
        }
        uploadResultsMap.computeIfAbsent(taskId, (k) -> prepareFakeInProgressResponse(taskId));

        EpaContext epaContext = fileUpload.getEpaContext();

        EpaAPI epaAPI = multiEpaService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());
        IDocumentManagementPortType documentManagementPortType = epaAPI.getDocumentManagementPortType();
        ((BindingProvider) documentManagementPortType).getRequestContext().putAll(epaContext.getRuntimeAttributes());

        try {
            String fileName = fileUpload.getFileName();
            String folderName = fileUpload.getFolderName();
            String telematikId = fileUpload.getTelematikId();
            String insurantId = fileUpload.getKvnr();
            String contentType = fileUpload.getContentType();
            String languageCode = fileUpload.getLanguageCode();
            byte[] documentBytes = fileUpload.getDocumentBytes();

            UCPersoenlicheVersichertendatenXML versichertendaten = epaContext.getInsuranceData().getPersoenlicheVersichertendaten();
            UCPersoenlicheVersichertendatenXML.Versicherter.Person person = versichertendaten.getVersicherter().getPerson();
            String firstName = person.getVorname();
            String lastName = person.getNachname();
            String title = person.getTitel();

            Pair<ProvideAndRegisterDocumentSetRequestType, StructureDefinition> pair = xdsDocumentService.get().prepareDocumentSetRequest(
                documentBytes,
                telematikId,
                insurantId,
                contentType,
                languageCode,
                firstName,
                lastName,
                title
            );

            ProvideAndRegisterDocumentSetRequestType request = pair.getLeft();
            StructureDefinition structureDefinition = pair.getRight();

            RegistryResponseType response = documentManagementPortType.documentRepositoryProvideAndRegisterDocumentSetB(request);
            RegistryErrorList registryErrorList = response.getRegistryErrorList();
            if (registryErrorList == null) {

                // NEW FILE: get fileName         | EXISTING FILE: existing fileName
                // NEW FILE: select webdav folder | EXISTING FILE: existing folder
                // NEW FILE: calculate checksum   | EXISTING FILE: check record in file and proceed if no record is present
                // NEW FILE: save                 | EXISTING FILE: no action
                // sync checksum file

                if (folderService.appendChecksumFor(telematikId, insurantId, documentBytes)) {
                    File medFolder = getMedFolder(folderName, telematikId, insurantId, structureDefinition);
                    File file = new File(medFolder, fileName);
                    if (!file.exists()) {
                        saveDataToFile(documentBytes, file);
                    }
                    log.info(String.format("[%s/%s] uploaded successfully", folderName, fileName));
                }
            } else {
                List<RegistryError> registryError = registryErrorList.getRegistryError();
                String msg = String.format("File upload failed: %s", registryError.getFirst().getErrorCode());
                throw new FileUploadException(msg, response);
            }

            uploadResultsMap.computeIfPresent(taskId, (k, prev) -> response);
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("[%s] Error while uploading %s", taskId, fileUpload), e);
            if (e instanceof FileUploadException uploadException) {
                RegistryResponseType response = uploadException.getResponse();
                uploadResultsMap.computeIfPresent(taskId, (k, prev) -> response);
            } else {
                uploadResultsMap.computeIfPresent(taskId, (k, prev) -> {
                    String startedAt = prev.getStatus().split(" ")[1];
                    return prepareFakeFailedResponse(taskId, startedAt, e.getMessage());
                });
            }
        }
    }

    private RegistryResponseType prepareFakeInProgressResponse(String taskId) {
        RegistryResponseType responseType = new RegistryResponseType();
        responseType.setRequestId(taskId);
        String startedAt = LocalDateTime.now().format(FORMATTER);
        responseType.setStatus(String.format("InProgress, startedAt=%s", startedAt));
        return responseType;
    }

    private RegistryResponseType prepareFakeFailedResponse(String taskId, String startedAt, String errorMessage) {
        RegistryResponseType responseType = new RegistryResponseType();
        RegistryError registryError = new RegistryError();
        registryError.setErrorCode("Failed");
        registryError.setValue(errorMessage);
        String failedAt = LocalDateTime.now().format(FORMATTER);
        responseType.setStatus(String.format("Failed, %s failedAt=%s", startedAt, failedAt));
        responseType.setRequestId(taskId);
        responseType.getRegistryErrorList().getRegistryError().add(registryError);
        return responseType;
    }

    @SuppressWarnings("unchecked")
    private File getMedFolder(
        String folderName,
        String telematikId,
        String insurantId,
        StructureDefinition definition
    ) {
        Map<String, String> map = (Map<String, String>) definition.getMetadata().getValue();
        String folderCode = folderName == null ? map.get("code") : folderName;
        File medFolder = folderService.getInsurantMedFolder(telematikId, insurantId, folderCode);
        if (medFolder == null || !medFolder.exists()) {
            String insurantFolderPath = folderService.getInsurantFolder(telematikId, insurantId).getAbsolutePath();
            File otherFolder = folderService.getInsurantMedFolder(telematikId, insurantId, "other");
            medFolder = folderService.createFolder(insurantFolderPath + File.separator + folderCode, otherFolder);
        }
        return medFolder;
    }

    public RegistryResponseType getResult(String taskUuid) {
        return uploadResultsMap.remove(taskUuid);
    }
}
