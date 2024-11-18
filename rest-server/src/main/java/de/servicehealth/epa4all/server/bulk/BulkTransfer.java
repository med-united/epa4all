package de.servicehealth.epa4all.server.bulk;

import de.servicehealth.epa4all.server.filetracker.FileDownload;
import de.servicehealth.epa4all.server.filetracker.FileUpload;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.rest.EpaContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBElement;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.IdentifiableType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotType1;
import org.jboss.logmanager.Level;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

@ApplicationScoped
public class BulkTransfer {

    private static final Logger log = Logger.getLogger(BulkTransfer.class.getName());

    private final FolderService folderService;
    private final Event<FileUpload> eventFileUpload;
    private final Event<FileDownload> eventFileDownload;

    @Inject
    public BulkTransfer(
        FolderService folderService,
        Event<FileUpload> eventFileUpload,
        Event<FileDownload> eventFileDownload
    ) {
        this.folderService = folderService;
        this.eventFileUpload = eventFileUpload;
        this.eventFileDownload = eventFileDownload;
    }

    public List<String> downloadInsurantFiles(
        EpaContext epaContext,
        String telematikId,
        String kvnr,
        List<JAXBElement<? extends IdentifiableType>> jaxbElements
    ) {
        Set<String> checksums = folderService.getChecksums(telematikId, kvnr);
        return jaxbElements.stream().map(e -> {
                Optional<SlotType1> fileNameOpt = e.getValue().getSlot().stream().filter(s -> s.getName().equals("URI")).findFirst();
                Optional<SlotType1> hashOpt = e.getValue().getSlot().stream().filter(s -> s.getName().equals("hash")).findFirst();
                if (fileNameOpt.isPresent() && hashOpt.isPresent()) {
                    String hash = hashOpt.get().getValueList().getValue().getFirst().toUpperCase();
                    if (checksums.contains(hash)) {
                        return null;
                    }
                    String taskId = UUID.randomUUID().toString();
                    String fileName = fileNameOpt.get().getValueList().getValue().getFirst();
                    eventFileDownload.fireAsync(new FileDownload(epaContext, taskId, fileName, telematikId, kvnr));
                    return taskId;
                } else {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
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

                String contentType = fileName.toLowerCase().endsWith(".xml")
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
