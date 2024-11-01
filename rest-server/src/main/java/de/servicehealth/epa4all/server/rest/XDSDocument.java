package de.servicehealth.epa4all.server.rest;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.idp.IdpClient;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.vsds.VSDService;
import de.servicehealth.model.EntitlementRequestType;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType.Document;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType.DocumentRequest;
import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.xml.bind.JAXBElement;
import oasis.names.tc.ebxml_regrep.xsd.lcm._3.SubmitObjectsRequest;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.AssociationType1;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExternalIdentifierType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.InternationalStringType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.LocalizedStringType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectListType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryPackageType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotListType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotType1;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ValueListType;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import org.apache.cxf.jaxrs.provider.JAXBElementProvider;
import org.xml.sax.SAXException;


@Path("xds-document")
public class XDSDocument extends AbstractResource {


    @GET
    @Path("{konnektor : ([0-9a-zA-Z\\-]+)?}{egkHandle : (/[0-9a-zA-Z\\-]+)?}")
    public String get(@PathParam("konnektor") String konnektor, @PathParam("egkHandle") String egkHandle) {
        try {
        	if(egkHandle != null) {
        		egkHandle = egkHandle.replaceAll("/", "");
        	}
            EpaAPI epaAPI = initAndGetEpaAPI(konnektor, egkHandle);
            
            RetrieveDocumentSetRequestType retrieveDocumentSetRequestType = new RetrieveDocumentSetRequestType();
            DocumentRequest documentRequest = new DocumentRequest();
            documentRequest.setDocumentUniqueId("2.25.62396952547397177119830569025634648826.332997229402574034029349705675377385445");
            documentRequest.setRepositoryUniqueId("1.2.276.0.76.3.1.315.3.2.1.1");
            retrieveDocumentSetRequestType.getDocumentRequest().add(documentRequest);
            RetrieveDocumentSetResponseType retrieveDocumentSetResponseType = epaAPI.getDocumentManagementPortType().documentRepositoryRetrieveDocumentSet(retrieveDocumentSetRequestType);
            return retrieveDocumentSetResponseType.getDocumentResponse().stream()
				.map(RetrieveDocumentSetResponseType.DocumentResponse::getDocumentUniqueId)
				.collect(Collectors.joining(", "));
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }
    
    // Based on: https://github.com/gematik/api-ePA/blob/ePA-2.6/samples/ePA%201%20Beispielnachrichten%20PS%20-%20Konnektor/Requests/provideandregister.xml
    @POST
    @Path("{konnektor : ([0-9a-zA-Z\\-]+)?}{egkHandle : (/[0-9a-zA-Z\\-]+)?}{folder : (/[0-9a-zA-Z\\-]+)?}")
    public RegistryResponseType post(@PathParam("konnektor") String konnektor, @PathParam("egkHandle") String egkHandle,  @PathParam("folder") String folder, InputStream is) {
    	try {
        	String kvnr = vsdService.getKVNR(UUID.randomUUID().toString(), egkHandle, null, defaultUserConfig);
    		if(egkHandle != null) {
        		egkHandle = egkHandle.replaceAll("/", "");
        	}
        	if(folder != null) {
        		folder = folder.replaceAll("/", "");
        	} else {
        		folder = "other";
        	}
            EpaAPI epaAPI = initAndGetEpaAPI(konnektor, egkHandle);
            
            
            Document document = new Document();
            String documentId = "DocumentEntry-0";
			document.setId(documentId);
            document.setValue(is.readAllBytes());
            
            
            SlotListType slotListType = new SlotListType();
            slotListType.getSlot().add(createSlotType("homeCommunityId", "urn:oid:1.2.276.0.76.3.1.315.3.2.1.1"));
            
            
            RegistryObjectListType registryObjectListType = new RegistryObjectListType();
            
            
            RegistryPackageType registryPackageType = new RegistryPackageType();
            SlotType1 submissionTime = new SlotType1();
            submissionTime.getValueList().getValue().add(getNumericISO8601Timestamp());
			registryPackageType.getSlot().add(submissionTime);
			
			ClassificationType classificationTypeAutor = new ClassificationType();
			classificationTypeAutor.setClassificationScheme("urn:uuid:a7058bb9-b4e4-4307-ba5b-e3f0ab85e12d");
			classificationTypeAutor.setClassifiedObject("submissionset");
			classificationTypeAutor.setId("author");
			classificationTypeAutor.setObjectType("urn:oasis:names:tc:ebxml-regrep:ObjectType:RegistryObject:Classification");
			classificationTypeAutor.getSlot().add(createSlotType("authorPerson", "^LastName^FirstName^^^Prof. Dr.^^^"));			
			registryPackageType.getClassification().add(classificationTypeAutor);
			
			ClassificationType classificationTypeContentType = new ClassificationType();
			classificationTypeContentType.setClassificationScheme("urn:uuid:aa543740-bdda-424e-8c96-df4873be850");
			classificationTypeContentType.setClassifiedObject("submissionset");
			classificationTypeContentType.setId("contentType");
			classificationTypeContentType.setNodeRepresentation("8");
			
			classificationTypeContentType.setObjectType("urn:oasis:names:tc:ebxml-regrep:ObjectType:RegistryObject:Classification");
			classificationTypeContentType.getSlot().add(createSlotType("codingScheme", "1.3.6.1.4.1.19376.3.276.1.5.12"));			
			classificationTypeContentType.getName().getLocalizedString().add(createLocalizedString("de", "Veranlassung durch Patient"));
			registryPackageType.getClassification().add(classificationTypeContentType);
			
			ClassificationType classificationTypeRegistryPackage = new ClassificationType();
			classificationTypeRegistryPackage.setClassificationScheme("urn:uuid:a54d6aa5-d40d-43f9-88c5-b4633d873bdd");
			classificationTypeRegistryPackage.setClassifiedObject("submissionset");
			classificationTypeRegistryPackage.setId("SubmissionSetClassification");
			classificationTypeRegistryPackage.setObjectType("urn:oasis:names:tc:ebxml-regrep:ObjectType:RegistryObject:Classification");
			registryPackageType.getClassification().add(classificationTypeRegistryPackage);
			
			ExternalIdentifierType externalIdentifierTypePatientId = new ExternalIdentifierType();
			externalIdentifierTypePatientId.setId("patientId");
			externalIdentifierTypePatientId.setIdentificationScheme("urn:uuid:6b5aea1a-874d-4603-a4bc-96a0a7b38446");
			externalIdentifierTypePatientId.setObjectType("urn:oasis:names:tc:ebxml-regrep:ObjectType:RegistryObject:ExternalIdentifier");
			externalIdentifierTypePatientId.setRegistryObject("submissionset");
			externalIdentifierTypePatientId.setValue(kvnr+"^^^&amp;1.2.276.0.76.4.8&amp;ISO");
			externalIdentifierTypePatientId.getName().getLocalizedString().add(createLocalizedString(null, "XDSSubmissionSet.patientId"));
			registryPackageType.getExternalIdentifier().add(externalIdentifierTypePatientId);
			
			ExternalIdentifierType externalIdentifierTypeUniqueId = new ExternalIdentifierType();
			externalIdentifierTypeUniqueId.setId("uniqueId");
			externalIdentifierTypeUniqueId.setIdentificationScheme("urn:uuid:96fdda7c-d067-4183-912e-bf5ee74998a8");
			externalIdentifierTypeUniqueId.setObjectType("urn:oasis:names:tc:ebxml-regrep:ObjectType:RegistryObject:ExternalIdentifier");
			externalIdentifierTypeUniqueId.setRegistryObject("submissionset");
			String uniqueIdValue = generateOID();
			externalIdentifierTypeUniqueId.setValue(uniqueIdValue);
			externalIdentifierTypeUniqueId.getName().getLocalizedString().add(createLocalizedString(null, "XDSSubmissionSet.uniqueId"));
			registryPackageType.getExternalIdentifier().add(externalIdentifierTypeUniqueId);
			
            
			AssociationType1 associationType1 = new AssociationType1();
			associationType1.setAssociationType("urn:oasis:names:tc:ebxml-regrep:AssociationType:HasMember");
			associationType1.setId("association-0");
			associationType1.setSourceObject("submissionset");
			associationType1.setTargetObject(documentId);
			associationType1.getSlot().add(createSlotType("SubmissionSetStatus", "Original"));
			
			JAXBElement<AssociationType1> jaxbElement = new JAXBElement<>(
                new QName("Association"), AssociationType1.class, associationType1
            );
			registryObjectListType.getIdentifiable().add(jaxbElement);
			
            ExtrinsicObjectType extrinsicObjectType = new ExtrinsicObjectType();
            extrinsicObjectType.setId(documentId);
            extrinsicObjectType.setMimeType("application/xml");
            extrinsicObjectType.setObjectType("urn:uuid:7edca82f-054d-47f2-a032-9b2a5b5186c1");
            extrinsicObjectType.setHome("urn:oid:1.2.276.0.76.3.1.315.3.2.1.1");
            
            extrinsicObjectType.getSlot().add(createSlotType("creationTime", getNumericISO8601Timestamp()));
            extrinsicObjectType.getSlot().add(createSlotType("languageCode", "de"));
            extrinsicObjectType.getSlot().add(createSlotType("URI", uniqueIdValue+".xml"));
            
            
            LocalizedStringType localizedStringType = new LocalizedStringType();
            localizedStringType.setLang("de");
            localizedStringType.setValue("Dokument "+uniqueIdValue);
			extrinsicObjectType.getName().getLocalizedString().add(localizedStringType);
			
			
			ClassificationType classificationTypeDocumentEntryClassCode = new ClassificationType();
			classificationTypeDocumentEntryClassCode.setClassificationScheme("urn:uuid:41a5887f-8865-4c09-adf7-e362475b143a");
			classificationTypeDocumentEntryClassCode.setClassifiedObject(documentId);
			classificationTypeDocumentEntryClassCode.setId("class-0");
			classificationTypeDocumentEntryClassCode.setNodeRepresentation("PLA");
			classificationTypeDocumentEntryClassCode.setObjectType("urn:oasis:names:tc:ebxml-regrep:ObjectType:RegistryObject:Classification");
			classificationTypeDocumentEntryClassCode.getSlot().add(createSlotType("codingScheme", "1.3.6.1.4.1.19376.3.276.1.5.8"));
			classificationTypeDocumentEntryClassCode.getName().getLocalizedString().add(createLocalizedString("de", "Planungsdokument"));
			registryPackageType.getClassification().add(classificationTypeDocumentEntryClassCode);
			
			
			// <!-- DocumentEntry.confidentialityCode -->
			// TODO
            
            registryObjectListType.getIdentifiable().add(jaxbElement);

            SubmitObjectsRequest submitObjectsRequest = new SubmitObjectsRequest();
            submitObjectsRequest.setRegistryObjectList(registryObjectListType);
            submitObjectsRequest.setRequestSlotList(slotListType);
            
            
            ProvideAndRegisterDocumentSetRequestType provideAndRegisterDocumentSetRequestType= new ProvideAndRegisterDocumentSetRequestType();
            provideAndRegisterDocumentSetRequestType.getDocument().add(document);
            provideAndRegisterDocumentSetRequestType.setSubmitObjectsRequest(submitObjectsRequest);
            
            IDocumentManagementPortType documentManagementPortType = epaAPI.getDocumentManagementPortType();
            RegistryResponseType registryResponseType = documentManagementPortType.documentRepositoryProvideAndRegisterDocumentSetB(provideAndRegisterDocumentSetRequestType);
            return registryResponseType;
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    	
    }
    
    public static String generateOID() {        
        // Beginne mit einem statischen Präfix, das den OID-Standard entspricht.
        StringBuilder oid = new StringBuilder("2.25");
        // https://www.itu.int/itu-t/recommendations/rec.aspx?rec=X.667
        UUID uuid = UUID.randomUUID();
        oid.append(uuid.getLeastSignificantBits());
        oid.append(".");
        oid.append(uuid.getMostSignificantBits());
        return oid.toString();
    }

	private LocalizedStringType createLocalizedString(String lang, String value) {
		LocalizedStringType localizedStringType = new LocalizedStringType();
		if(lang != null) {
			localizedStringType.setLang(lang);
		}
		localizedStringType.setValue(value);
		return localizedStringType;
	}

	public static SlotType1 createSlotType(String value, String string) {
		SlotType1 slotType = new SlotType1();
		slotType.setName(value);
		slotType.getValueList().getValue().add(string);
		return slotType;
	}
    
    public static String getNumericISO8601Timestamp() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        return now.format(formatter);
    }
  
}
