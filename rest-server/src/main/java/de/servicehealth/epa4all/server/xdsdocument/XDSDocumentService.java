package de.servicehealth.epa4all.server.xdsdocument;

import de.servicehealth.epa4all.xds.CustomCodingScheme;
import de.servicehealth.epa4all.xds.ProvideAndRegisterSingleDocumentTypeBuilder;
import de.servicehealth.epa4all.xds.author.AuthorPerson;
import de.servicehealth.epa4all.xds.ebrim.FolderDefinition;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType.Document;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryRequest;
import oasis.names.tc.ebxml_regrep.xsd.query._3.ResponseOptionType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.AdhocQueryType;

import java.util.List;

import static de.servicehealth.epa4all.xds.XDSUtils.createSlotType;
import static de.servicehealth.epa4all.xds.XDSUtils.generateUrnUuid;

@Dependent
public class XDSDocumentService {

    @Inject
    ProvideAndRegisterSingleDocumentTypeBuilder provideAndRegisterDocumentBuilder;

    public RetrieveDocumentSetRequestType prepareRetrieveDocumentSetRequestType(String uniqueId, String repositoryUniqueId) {
        RetrieveDocumentSetRequestType retrieveDocumentSetRequestType = new RetrieveDocumentSetRequestType();
        RetrieveDocumentSetRequestType.DocumentRequest documentRequest = new RetrieveDocumentSetRequestType.DocumentRequest();
        documentRequest.setDocumentUniqueId(uniqueId);
        documentRequest.setRepositoryUniqueId(repositoryUniqueId);
        retrieveDocumentSetRequestType.getDocumentRequest().add(documentRequest);
        return retrieveDocumentSetRequestType;
    }

    // https://github.com/gematik/api-ePA/blob/ePA-2.6/samples/ePA%201%20Beispielnachrichten%20PS%20-%20Konnektor/Requests/adhocquery.xml
    public AdhocQueryRequest prepareAdhocQueryRequest(String kvnr) {
        AdhocQueryRequest adhocQueryRequest = new AdhocQueryRequest();
        ResponseOptionType responseOptionType = new ResponseOptionType();
        responseOptionType.setReturnType("LeafClass");
        responseOptionType.setReturnComposedObjects(true);

        adhocQueryRequest.setResponseOption(responseOptionType);

        AdhocQueryType adhocQueryType = new AdhocQueryType();
        // FindDocuments
        adhocQueryType.setId("urn:uuid:14d4debf-8f97-4251-9a74-a90016b0af0d");
        adhocQueryType.getSlot().add(createSlotType("$XDSDocumentEntryPatientId", "'" + kvnr + "^^^&1.2.276.0.76.4.8&ISO'"));
        adhocQueryType.getSlot().add(createSlotType("$XDSDocumentEntryStatus", "('urn:oasis:names:tc:ebxml-regrep:StatusType:Approved')"));
        adhocQueryRequest.setAdhocQuery(adhocQueryType);
        return adhocQueryRequest;
    }

    public ProvideAndRegisterDocumentSetRequestType prepareDocumentSetRequest(
        List<FolderDefinition> folderDefinitions,
        byte[] documentBytes,
        String telematikId,
        String kvnr,
        String fileName,
        String title,
        String authorInstitution,
        String authorLanr,
        String authorFirstName,
        String authorLastName,
        String authorTitle,
        String praxis,
        String practiceSetting,
        String information,
        String information2,
        List<CustomCodingScheme> customCodingSchemes,
        String contentType,
        String languageCode,
        String authorVsdFirstName,
        String authorVsdLastName,
        String authorVsdTitle
    ) {
        Document document = new Document();
        document.setValue(documentBytes);
        String documentId = generateUrnUuid();
        document.setId(documentId);

        AuthorPerson authorPerson = authorFirstName == null && authorLastName == null
            ? new AuthorPerson("123456667", authorVsdFirstName, authorVsdLastName, authorVsdTitle, "PRA")
            : new AuthorPerson(authorLanr == null ? "123456667" : authorLanr, authorFirstName, authorLastName, authorTitle, "PRA");

        provideAndRegisterDocumentBuilder.init(
            document,
            folderDefinitions,
            authorPerson,
            authorInstitution,
            telematikId,
            documentId,
            fileName,
            title,
            praxis,
            practiceSetting,
            information,
            information2,
            customCodingSchemes,
            contentType,
            languageCode,
            kvnr
        );
        return provideAndRegisterDocumentBuilder.build();
    }
}