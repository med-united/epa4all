package de.servicehealth.epa4all.server.filetracker;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.server.xdsdocument.XDSDocumentService;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import de.servicehealth.epa4all.xds.structure.StructureDefinitionService;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.xml.ws.BindingProvider;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryError;
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
import static de.servicehealth.epa4all.xds.XDSUtils.isPdfCompliant;
import static de.servicehealth.epa4all.xds.XDSUtils.isXmlCompliant;

@ApplicationScoped
public class EpaFileTracker {

    private static final Logger log = Logger.getLogger(EpaFileTracker.class.getName());

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<String, RegistryResponseType> resultsMap = new ConcurrentHashMap<>();

    private static final ThreadFactory threadFactory = Thread.ofVirtual().name("upload-file-", 0).factory();
    private static final ExecutorService filesTransferExecutor = Executors.newThreadPerTaskExecutor(threadFactory);

    private final FolderService folderService;
    private final MultiEpaService multiEpaService;
    private final Instance<XDSDocumentService> xdsDocumentService;
    private final StructureDefinitionService structureDefinitionService;

    @Inject
    public EpaFileTracker(
        FolderService folderService,
        MultiEpaService multiEpaService,
        Instance<XDSDocumentService> xdsDocumentService,
        StructureDefinitionService structureDefinitionService
    ) {
        this.folderService = folderService;
        this.multiEpaService = multiEpaService;
        this.xdsDocumentService = xdsDocumentService;
        this.structureDefinitionService = structureDefinitionService;
    }

    void onStart(@Observes StartupEvent ev) {
    }

    public void onUpload(@ObservesAsync FileUpload fileUpload) {
        filesTransferExecutor.submit(() ->
            transferFile(fileUpload.getTaskId(), fileUpload.getEpaContext(), fileUpload, (documentPortType, epaContext, taskId) -> {
                String contentType = fileUpload.getContentType();
                byte[] documentBytes = fileUpload.getDocumentBytes();
                StructureDefinition structureDefinition = structureDefinitionService.getStructureDefinition(contentType, documentBytes);
                ProvideAndRegisterDocumentSetRequestType request = prepareProvideAndRegisterDocumentSetRequest(
                    fileUpload, epaContext, structureDefinition
                );
                RegistryResponseType response = documentPortType.documentRepositoryProvideAndRegisterDocumentSetB(request);
                handleUploadResponse(taskId, fileUpload, response, structureDefinition);
            }));
    }

    public void onDownload(@ObservesAsync FileDownload fileDownload) {
        filesTransferExecutor.submit(() ->
            transferFile(fileDownload.getTaskId(), fileDownload.getEpaContext(), fileDownload, (documentPortType, epaContext, taskId) -> {
                String uniqueId = fileDownload.getFileName().replace(".xml", "").replace(".pdf", "");
                RetrieveDocumentSetRequestType requestType = xdsDocumentService.get().prepareRetrieveDocumentSetRequestType(uniqueId);
                RetrieveDocumentSetResponseType responseType = documentPortType.documentRepositoryRetrieveDocumentSet(requestType);
                handleDownloadResponse(taskId, fileDownload, responseType);
            }));
    }

    private interface TransferAction {
        void execute(IDocumentManagementPortType documentManagementPortType, EpaContext epaContext, String taskId) throws Exception;
    }

    private void transferFile(String taskId, EpaContext epaContext, FileAction fileAction, TransferAction transferAction) {
        if (resultsMap.containsKey(taskId)) {
            log.warning(String.format("Already in progress %s", fileAction.toString()));
            return;
        }
        resultsMap.computeIfAbsent(taskId, (k) -> prepareInProgressResponse(taskId));
        IDocumentManagementPortType documentManagementPortType = getDocumentManagementPortType(epaContext);
        try {
            transferAction.execute(documentManagementPortType, epaContext, taskId);
        } catch (Exception e) {
            String s = fileAction.isUpload() ? "uploading" : "downloading";
            log.log(Level.SEVERE, String.format("[%s] Error while %s %s", taskId, s, fileAction), e);

            if (e instanceof FileTransferException downloadException) {
                RegistryResponseType response = downloadException.getResponse();
                resultsMap.computeIfPresent(taskId, (k, prev) -> response);
            } else {
                resultsMap.computeIfPresent(taskId, (k, prev) -> {
                    String startedAt = prev.getStatus().split(" ")[1];
                    return prepareFailedResponse(taskId, startedAt, e.getMessage());
                });
            }
        }
    }

    private void handleDownloadResponse(
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
        } else {
            RegistryError registryError = registryResponse.getRegistryErrorList().getRegistryError().getFirst();
            String error = registryError.getErrorCode() + " : " + registryError.getCodeContext();
            String msg = String.format("File download failed: %s -> %s", fileDownload.getFileName(), error);
            throw new FileTransferException(msg, registryResponse);
        }
        resultsMap.computeIfPresent(taskId, (k, prev) -> registryResponse);
    }

    @SuppressWarnings("unchecked")
    private String getFolderCode(StructureDefinition structureDefinition) {
        Map<String, String> map = (Map<String, String>) structureDefinition.getMetadata().getValue();
        return map.get("code");
    }

    private void handleUploadResponse(
        String taskId,
        FileUpload fileUpload,
        RegistryResponseType registryResponse,
        StructureDefinition structureDefinition
    ) throws Exception {
        boolean success = registryResponse.getStatus().contains("Success");
        if (success) {
            // NEW FILE: get fileName         | EXISTING FILE: existing fileName
            // NEW FILE: select webdav folder | EXISTING FILE: existing folder
            // NEW FILE: calculate checksum   | EXISTING FILE: check record in file and proceed if no record is present
            // NEW FILE: save                 | EXISTING FILE: no action
            // sync checksum file

            String fileName = fileUpload.getFileName();
            String folderName = fileUpload.getFolderName();
            String telematikId = fileUpload.getTelematikId();
            String insurantId = fileUpload.getKvnr();
            byte[] documentBytes = fileUpload.getDocumentBytes();

            String folderCode = folderName == null ? getFolderCode(structureDefinition) : folderName;
            storeNewFile(fileName, folderCode, telematikId, insurantId, documentBytes);
            log.info(String.format("[%s/%s] uploaded successfully", folderCode, fileName));
        } else {
            RegistryError registryError = registryResponse.getRegistryErrorList().getRegistryError().getFirst();
            String error = registryError.getErrorCode() + " : " + registryError.getCodeContext();
            String msg = String.format("File upload failed: %s", error);
            throw new FileTransferException(msg, registryResponse);
        }
        resultsMap.computeIfPresent(taskId, (k, prev) -> registryResponse);
    }

    private void storeNewFile(
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

    private IDocumentManagementPortType getDocumentManagementPortType(EpaContext epaContext) {
        EpaAPI epaAPI = multiEpaService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());
        IDocumentManagementPortType documentManagementPortType = epaAPI.getDocumentManagementPortType();
        ((BindingProvider) documentManagementPortType).getRequestContext().putAll(epaContext.getRuntimeAttributes());
        return documentManagementPortType;
    }

    private ProvideAndRegisterDocumentSetRequestType prepareProvideAndRegisterDocumentSetRequest(
        FileUpload fileUpload,
        EpaContext epaContext,
        StructureDefinition structureDefinition
    ) {
        String telematikId = fileUpload.getTelematikId();
        String insurantId = fileUpload.getKvnr();
        String fileName = fileUpload.getFileName();
        String contentType = fileUpload.getContentType();
        String languageCode = fileUpload.getLanguageCode();
        byte[] documentBytes = fileUpload.getDocumentBytes();

        UCPersoenlicheVersichertendatenXML versichertendaten = epaContext.getInsuranceData().getPersoenlicheVersichertendaten();
        UCPersoenlicheVersichertendatenXML.Versicherter.Person person = versichertendaten.getVersicherter().getPerson();
        String firstName = person.getVorname();
        String lastName = person.getNachname();
        String title = person.getTitel();

        return xdsDocumentService.get().prepareDocumentSetRequest(
            structureDefinition.getElements().getFirst().getMetadata(),
            documentBytes,
            telematikId,
            insurantId,
            fileName,
            contentType,
            languageCode,
            firstName,
            lastName,
            title
        );
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
        responseType.getRegistryErrorList().getRegistryError().add(registryError);
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

    public RegistryResponseType getResult(String taskUuid) {
        return resultsMap.remove(taskUuid);
    }
}
