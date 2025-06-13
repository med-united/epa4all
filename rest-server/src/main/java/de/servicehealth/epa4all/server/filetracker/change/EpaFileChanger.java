package de.servicehealth.epa4all.server.filetracker.change;

import de.servicehealth.epa4all.server.filetracker.EpaFileTracker;
import de.servicehealth.epa4all.server.filetracker.delete.EpaFileRemover;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import jakarta.enterprise.context.ApplicationScoped;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class EpaFileChanger extends EpaFileTracker<FileChange> {

    private static final Logger log = LoggerFactory.getLogger(EpaFileRemover.class.getName());

    @Override
    protected RegistryResponseType handleTransfer(
        FileChange fileChange,
        IDocumentManagementPortType documentPortType
    ) throws Exception {
        RegistryResponseType response = documentPortType.updateResponderRestrictedUpdateDocumentSet(fileChange.getRequest());
        handleChangeResponse(fileChange, response);
        return response;
    }

    private void handleChangeResponse(FileChange fileChange, RegistryResponseType registryResponse) {
        boolean success = registryResponse.getStatus().contains("Success");
        if (success) {
            log.info("[%s] Metadata for %s is successfully changed".formatted(fileChange.getKvnr(), fileChange.getFileName()));
        }
    }
}