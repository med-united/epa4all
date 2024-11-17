package de.servicehealth.epa4all.server.bulk;

import de.servicehealth.epa4all.server.filetracker.FileUpload;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.rest.EpaContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jboss.logmanager.Level;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

@ApplicationScoped
public class BulkUploader {

    private static final Logger log = Logger.getLogger(BulkUploader.class.getName());

    private final FolderService folderService;
    private final Event<FileUpload> eventFileUpload;

    @Inject
    public BulkUploader(
        Event<FileUpload> eventFileUpload,
        FolderService folderService
    ) {
        this.eventFileUpload = eventFileUpload;
        this.folderService = folderService;
    }

    public List<String> uploadInsurantFiles(
        EpaContext epaContext,
        String telematikId,
        String kvnr,
        String languageCode
    ) {
        String insurantId = epaContext.getInsuranceData().getInsurantId();
        List<File> files = folderService.getInsurantMedFoldersFiles(telematikId, insurantId, Set.of(".xml", ".pdf"));
        return files.stream()
            .map(f -> {
                String folderName = f.getParentFile().getName();
                String fileName = f.getName();

                log.info(String.format("[%s] Uploading [%s/%s]", kvnr, folderName, fileName));

                String contentType = fileName.endsWith("xml")
                    ? "application/xml"
                    : "application/pdf";
                try {
                    byte[] documentBytes = Files.readAllBytes(f.toPath());
                    String taskId = UUID.randomUUID().toString();
                    eventFileUpload.fireAsync(new FileUpload(
                        epaContext, taskId, contentType, languageCode, telematikId, kvnr, fileName, folderName, documentBytes
                    ));
                    return taskId;
                } catch (Exception e) {
                    String msg = String.format(
                        "[%s] Error while preparing file [%s/%s] to upload", kvnr, folderName, fileName
                    );
                    log.log(Level.SEVERE, msg);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }
}
