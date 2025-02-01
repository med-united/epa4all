package de.servicehealth.epa4all.server.filetracker.upload;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.servicehealth.epa4all.server.filetracker.EpaFileTracker;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import jakarta.enterprise.context.ApplicationScoped;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;

import java.util.logging.Logger;

@ApplicationScoped
public class EpaFileUploader extends EpaFileTracker<FileUpload> {

    private static final Logger log = Logger.getLogger(EpaFileUploader.class.getName());

    @Override
    protected RegistryResponseType handleTransfer(FileUpload fileUpload, IDocumentManagementPortType documentPortType) throws Exception {
        String contentType = fileUpload.getContentType();
        EpaContext epaContext = fileUpload.getEpaContext();
        byte[] documentBytes = fileUpload.getDocumentBytes();
        StructureDefinition structureDefinition = structureDefinitionService.getStructureDefinition(contentType, documentBytes);
        ProvideAndRegisterDocumentSetRequestType request = prepareProvideAndRegisterDocumentSetRequest(
            fileUpload, epaContext, structureDefinition
        );
        RegistryResponseType response = documentPortType.documentRepositoryProvideAndRegisterDocumentSetB(request);
        handleUploadResponse(fileUpload, response, structureDefinition);
        return response;
    }

    private void handleUploadResponse(
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
            folderService.storeNewFile(fileName, folderCode, telematikId, insurantId, documentBytes);
            log.info(String.format("[%s/%s] uploaded successfully", folderCode, fileName));
        }
    }

    private ProvideAndRegisterDocumentSetRequestType prepareProvideAndRegisterDocumentSetRequest(
        FileUpload fileUpload,
        EpaContext epaContext,
        StructureDefinition structureDefinition
    ) {
        String kvnr = fileUpload.getKvnr();
        UCPersoenlicheVersichertendatenXML versichertendaten = epaContext.getInsuranceData().getPersoenlicheVersichertendaten();
        if (versichertendaten != null) {
            UCPersoenlicheVersichertendatenXML.Versicherter.Person person = versichertendaten.getVersicherter().getPerson();
            return xdsDocumentService.get().prepareDocumentSetRequest(
                structureDefinition.getElements().getFirst().getMetadata(),
                fileUpload.getDocumentBytes(),
                fileUpload.getTelematikId(),
                kvnr,
                fileUpload.getFileName(),
                fileUpload.getContentType(),
                fileUpload.getLanguageCode(),
                person.getVorname(),
                person.getNachname(),
                person.getTitel()
            );
        }
        throw new IllegalStateException(String.format("[%s] versichertendaten is empty, please insert the card of the\npatient into a eHealth Card Terminal to generate this data.", kvnr));
    }
}