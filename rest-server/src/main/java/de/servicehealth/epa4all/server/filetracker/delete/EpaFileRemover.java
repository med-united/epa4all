package de.servicehealth.epa4all.server.filetracker.delete;

import de.servicehealth.epa4all.server.filetracker.EpaFileTracker;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import jakarta.enterprise.context.ApplicationScoped;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class EpaFileRemover extends EpaFileTracker<FileDelete> {

    private static final Logger log = LoggerFactory.getLogger(EpaFileRemover.class.getName());

    @Override
    protected RegistryResponseType handleTransfer(
        FileDelete fileDelete,
        IDocumentManagementPortType documentPortType
    ) throws Exception {
        RegistryResponseType response = documentPortType.documentRegistryDeleteDocumentSet(fileDelete.getRequest());
        handleDeleteResponse(fileDelete, response);
        return response;
    }

    private void handleDeleteResponse(
        FileDelete fileDelete,
        RegistryResponseType registryResponse
    ) {
        boolean success = registryResponse.getStatus().contains("Success");
        if (success) {
            String telematikId = fileDelete.getTelematikId();
            String insurantId = fileDelete.getKvnr();

            String fileName = fileDelete.getFileName();
            if (fileName != null && !fileName.isEmpty()) {
                Set<String> fileNames = Arrays.stream(fileName.split(",")).collect(Collectors.toSet());
                List<File> files = folderService.getMedFilesXmlPdf(telematikId, insurantId);
                for (String fName : fileNames) {
                    files.stream().filter(file -> file.getName().equalsIgnoreCase(fName)).findFirst().ifPresent(f -> {
                        try {
                            folderService.deleteFile(telematikId, insurantId, f.getAbsolutePath());
                        } catch (Exception e) {
                            log.error("Error while deleting %s".formatted(f.getAbsolutePath()), e);
                        }
                    });
                }
            }
        }
    }
}