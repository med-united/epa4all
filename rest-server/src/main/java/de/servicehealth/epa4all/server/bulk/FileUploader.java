package de.servicehealth.epa4all.server.bulk;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.servicehealth.epa4all.server.filetracker.FileUpload;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.server.xdsdocument.XDSDocumentService;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;

import java.util.UUID;

@ApplicationScoped
public class FileUploader {

    @Inject
    Event<FileUpload> eventFileUpload;

    public String submitFile(
        XDSDocumentService xdsDocumentService,
        EpaContext epaContext,
        String telematikId,
        String kvnr,
        String contentType,
        String languageCode,
        String fileName,
        String folderName,
        byte[] documentBytes
    ) throws Exception {
        String taskId = UUID.randomUUID().toString();

        UCPersoenlicheVersichertendatenXML versichertendaten = epaContext.getInsuranceData().getPersoenlicheVersichertendaten();
        UCPersoenlicheVersichertendatenXML.Versicherter.Person person = versichertendaten.getVersicherter().getPerson();
        String firstName = person.getVorname();
        String lastName = person.getNachname();
        String title = person.getTitel();

        Pair<ProvideAndRegisterDocumentSetRequestType, StructureDefinition> pair = xdsDocumentService.prepareDocumentSetRequest(
            documentBytes,
            telematikId,
            kvnr,
            contentType,
            languageCode,
            firstName,
            lastName,
            title
        );

        ProvideAndRegisterDocumentSetRequestType request = pair.getLeft();
        StructureDefinition structureDefinition = pair.getRight();

        eventFileUpload.fireAsync(new FileUpload(
            taskId, contentType, languageCode, telematikId, kvnr, fileName, folderName,
            epaContext, documentBytes, request, structureDefinition
        ));
        return taskId;
    }
}
