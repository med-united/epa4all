package de.servicehealth.epa4all.server.filetracker.upload;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.servicehealth.epa4all.server.filetracker.EpaFileTracker;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import jakarta.enterprise.context.ApplicationScoped;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class EpaFileUploader extends EpaFileTracker<FileUpload> {

    private static final Logger log = LoggerFactory.getLogger(EpaFileUploader.class.getName());

    @Override
    protected RegistryResponseType handleTransfer(FileUpload fileUpload, IDocumentManagementPortType documentPortType) throws Exception {
        String contentType = fileUpload.getContentType();
        EpaContext epaContext = fileUpload.getEpaContext();
        byte[] documentBytes = fileUpload.getDocumentBytes();
        StructureDefinition structureDefinition = structureDefinitionService.getStructureDefinition(
            fileUpload.getIg(), contentType, documentBytes
        );
        ProvideAndRegisterDocumentSetRequestType request = prepareProvideAndRegisterDocumentSetRequest(
            fileUpload, epaContext, structureDefinition
        );
        RegistryResponseType response = documentPortType.documentRepositoryProvideAndRegisterDocumentSetB(request);
        handleUploadResponse(fileUpload, fileUpload.getFolderName(), fileUpload.getDocumentBytes(), response, structureDefinition);
        return response;
    }

    private ProvideAndRegisterDocumentSetRequestType prepareProvideAndRegisterDocumentSetRequest(
        FileUpload fileUpload,
        EpaContext epaContext,
        StructureDefinition structureDefinition
    ) {
        String kvnr = fileUpload.getKvnr();
        UCPersoenlicheVersichertendatenXML.Versicherter.Person person = getPerson(epaContext, kvnr);
        return xdsDocumentService.get().prepareDocumentSetRequest(
            structureDefinition.getElements().getFirst().getMetadata(),
            fileUpload.getDocumentBytes(),
            fileUpload.getTelematikId(),
            kvnr,
            fileUpload.getFileName(),
            fileUpload.getTitle(),
            fileUpload.getAuthorLanr(),
            fileUpload.getAuthorFirstName(),
            fileUpload.getAuthorLastName(),
            fileUpload.getAuthorTitle(),
            fileUpload.getPraxis(),
            fileUpload.getPracticeSetting(),
            fileUpload.getInformation(),
            fileUpload.getInformation2(),
            fileUpload.getContentType(),
            fileUpload.getLanguageCode(),
            person.getVorname(),
            person.getNachname(),
            person.getTitel()
        );
    }

    private static UCPersoenlicheVersichertendatenXML.Versicherter.Person getPerson(EpaContext epaContext, String kvnr) {
        InsuranceData insuranceData = epaContext.getInsuranceData();
        if (insuranceData == null || insuranceData.getPersoenlicheVersichertendaten() == null) {
            String msg = "[%s] versichertendaten is empty, please insert the card of the\npatient into a eHealth Card Terminal to generate this data.";
            throw new IllegalStateException(String.format(msg, kvnr));
        }
        return insuranceData.getPersoenlicheVersichertendaten().getVersicherter().getPerson();
    }
}