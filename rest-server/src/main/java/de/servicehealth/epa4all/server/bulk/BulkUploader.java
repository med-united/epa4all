package de.servicehealth.epa4all.server.bulk;

import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.server.xdsdocument.RegularXDSDocumentService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logmanager.Level;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

@ApplicationScoped
public class BulkUploader {

    private static final Logger log = Logger.getLogger(BulkUploader.class.getName());

    private final Instance<RegularXDSDocumentService> xdsDocumentService;
    private final FolderService folderService;
    private final FileUploader fileUploader;

    @Inject
    public BulkUploader(
        Instance<RegularXDSDocumentService> xdsDocumentService,
        FolderService folderService,
        FileUploader fileUploader
    ) {
        this.xdsDocumentService = xdsDocumentService;
        this.folderService = folderService;
        this.fileUploader = fileUploader;
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
                    return fileUploader.submitFile(
                        xdsDocumentService.get(),
                        epaContext,
                        telematikId,
                        kvnr,
                        contentType,
                        languageCode,
                        fileName,
                        folderName,
                        documentBytes
                    );
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
