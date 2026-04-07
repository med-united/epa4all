package de.servicehealth.epa4all.server.filetracker.upload;

import de.servicehealth.epa4all.server.filetracker.EpaFileTracker;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import jakarta.enterprise.context.ApplicationScoped;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;

@ApplicationScoped
public class EpaFileRawUploader extends EpaFileTracker<FileRawUpload> {

    @Override
    protected RegistryResponseType handleTransfer(FileRawUpload fileUpload, IDocumentManagementPortType documentPortType) throws Exception {
        StructureDefinition structureDefinition = structureDefinitionService.getStructureDefinition(
            fileUpload.getTaskId(), fileUpload.getIg(), fileUpload.getExtrinsicContext()
        );
        RegistryResponseType response = documentPortType.documentRepositoryProvideAndRegisterDocumentSetB(fileUpload.getRequest());
        handleUploadResponse(fileUpload, fileUpload.getDocumentBytes(), response, structureDefinition);
        return response;
    }
}
