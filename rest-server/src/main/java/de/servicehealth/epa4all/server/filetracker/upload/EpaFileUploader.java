package de.servicehealth.epa4all.server.filetracker.upload;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.servicehealth.epa4all.server.filetracker.EpaFileTracker;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import jakarta.enterprise.context.RequestScoped;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;

import java.util.logging.Logger;

@RequestScoped
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
            storeNewFile(fileName, folderCode, telematikId, insurantId, documentBytes);
            log.info(String.format("[%s/%s] uploaded successfully", folderCode, fileName));
        }
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
}
