package de.servicehealth.epa4all.server.xdsdocument;

import de.servicehealth.epa4all.xds.ProvideAndRegisterSingleDocumentTypeBuilder;
import de.servicehealth.epa4all.xds.author.AuthorPerson;
import de.servicehealth.epa4all.xds.ebrim.FolderDefinition;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType.Document;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType;
import jakarta.enterprise.context.Dependent;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryRequest;
import oasis.names.tc.ebxml_regrep.xsd.query._3.ResponseOptionType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.AdhocQueryType;

import java.util.List;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.xds.XDSUtils.createSlotType;
import static de.servicehealth.epa4all.xds.XDSUtils.generateUrnUuid;

@Dependent
public class XDSDocumentService {

    private static final Logger log = Logger.getLogger(XDSDocumentService.class.getName());

    private final ProvideAndRegisterSingleDocumentTypeBuilder provideAndRegisterDocumentBuilder;

    public XDSDocumentService(ProvideAndRegisterSingleDocumentTypeBuilder provideAndRegisterDocumentBuilder) {
        this.provideAndRegisterDocumentBuilder = provideAndRegisterDocumentBuilder;
    }

    public RetrieveDocumentSetRequestType prepareRetrieveDocumentSetRequestType(String uniqueId) {
        RetrieveDocumentSetRequestType retrieveDocumentSetRequestType = new RetrieveDocumentSetRequestType();
        RetrieveDocumentSetRequestType.DocumentRequest documentRequest = new RetrieveDocumentSetRequestType.DocumentRequest();
        documentRequest.setDocumentUniqueId(uniqueId);
        documentRequest.setRepositoryUniqueId("1.2.276.0.76.3.1.466.2.1.4.90.1");
        retrieveDocumentSetRequestType.getDocumentRequest().add(documentRequest);
        return retrieveDocumentSetRequestType;
    }

    public AdhocQueryRequest prepareAdhocQueryRequest(String kvnr) {
        AdhocQueryRequest adhocQueryRequest = new AdhocQueryRequest();
            /* https://github.com/gematik/api-ePA/blob/ePA-2.6/samples/ePA%201%20Beispielnachrichten%20PS%20-%20Konnektor/Requests/adhocquery.xml
              <query:ResponseOption returnType="LeafClass" returnComposedObjects="true"/>
		      <rim:AdhocQuery id="urn:uuid:14d4debf-8f97-4251-9a74-a90016b0af0d" home="urn:oid:1.2.276.0.76.3.1.405">
		        <rim:Slot name="$XDSDocumentEntryPatientId">
		          <rim:ValueList>
		            <rim:Value>'X110473550^^^&amp;1.2.276.0.76.4.8&amp;ISO'</rim:Value>
		          </rim:ValueList>
		        </rim:Slot>
		        <rim:Slot name="$XDSDocumentEntryStatus">
		          <rim:ValueList>
		            <rim:Value>('urn:oasis:names:tc:ebxml-regrep:StatusType:Approved')</rim:Value>
		          </rim:ValueList>
		        </rim:Slot>
             */
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
        String contentType,
        String languageCode,
        String firstName,
        String lastName,
        String title
    ) {
        Document document = new Document();
        document.setValue(documentBytes);
        String documentId = generateUrnUuid();
        document.setId(documentId);

        AuthorPerson authorPerson = new AuthorPerson("123456667", firstName, lastName, title, "PRA"); // TODO

        provideAndRegisterDocumentBuilder.init(
            document,
            folderDefinitions,
            authorPerson,
            telematikId,
            documentId,
            fileName,
            contentType,
            languageCode,
            kvnr
        );
        return provideAndRegisterDocumentBuilder.build();
    }
}
