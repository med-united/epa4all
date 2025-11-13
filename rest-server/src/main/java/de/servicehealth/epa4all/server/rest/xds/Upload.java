package de.servicehealth.epa4all.server.rest.xds;

import de.servicehealth.epa4all.server.filetracker.upload.FileRawUpload;
import de.servicehealth.epa4all.server.filetracker.upload.FileUpload;
import de.servicehealth.epa4all.server.rest.EpaContext;
import de.servicehealth.epa4all.xds.CustomCodingScheme;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType.Document;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.xml.bind.JAXBElement;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.IdentifiableType;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.servicehealth.epa4all.server.filetracker.upload.soap.RawSoapUtils.deserializeRegisterElement;
import static de.servicehealth.epa4all.server.rest.xds.XdsResource.XDS_DOCUMENT_PATH;
import static de.servicehealth.epa4all.xds.CodingScheme.ClassCodeClassification;
import static de.servicehealth.epa4all.xds.CodingScheme.FacilityTypeCodeClassification;
import static de.servicehealth.epa4all.xds.CodingScheme.PracticeSettingClassification;
import static de.servicehealth.epa4all.xds.CodingScheme.TypeCodeClassification;
import static de.servicehealth.epa4all.xds.XDSUtils.isPdfCompliant;
import static de.servicehealth.epa4all.xds.XDSUtils.isXmlCompliant;
import static de.servicehealth.vau.VauClient.KVNR;
import static de.servicehealth.vau.VauClient.UPLOAD_CONTENT_TYPE;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.MEDIA_TYPE_WILDCARD;
import static jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.eclipse.microprofile.openapi.annotations.enums.ParameterIn.HEADER;
import static org.eclipse.microprofile.openapi.annotations.enums.ParameterIn.QUERY;

@SuppressWarnings("unused")
@RequestScoped
@Path(XDS_DOCUMENT_PATH)
public class Upload extends XdsResource {

    @APIResponses({
        @APIResponse(responseCode = "200", description = "Task uuid"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @POST
    @Consumes(MULTIPART_FORM_DATA)
    @Produces(APPLICATION_JSON)
    @Path("upload/raw")
    @Operation(summary = "Upload single document XML/PDF/etc providing SOAP request to the XDS registry")
    public String uploadFiles(
        @Parameter(
            name = "Content-Type",
            description = "MIME type of the uploaded document",
            example = "application/pdf",
            in = HEADER,
            required = true
        )
        @HeaderParam(CONTENT_TYPE) String contentType,
        @Parameter(
            name = "Lang-Code",
            description = "Language code for the document",
            example = "de-DE",
            in = HEADER,
            required = true
        )
        @HeaderParam("Lang-Code") String languageCode,
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)"
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = KVNR, description = "Patient KVNR", required = true)
        @QueryParam(KVNR) String kvnr,
        @Parameter(name = "ig", description = "IG schema name")
        @QueryParam("ig") String ig,
        @Parameter(description = "MultipartFormData: 'pdf_body' and 'raw_soap' ns5:ProvideAndRegisterDocumentSetRequest XML element to submit to the XDS registry")
        MultipartFormDataInput input
    ) throws Exception {
        EpaContext epaContext = prepareEpaContext(kvnr);
        epaContext.getXHeaders().put(UPLOAD_CONTENT_TYPE, contentType);

        InputPart bytesPart = input.getFormDataMap().get("pdf_body").getFirst();
        String fileName = bytesPart.getFileName();
        InputStream is = bytesPart.getBody(InputStream.class, null);
        byte[] documentBytes = is.readAllBytes();

        InputPart soapPart = input.getFormDataMap().get("raw_soap").getFirst();
        String xmlElement = soapPart.getBody(String.class, null);
        ProvideAndRegisterDocumentSetRequestType request = deserializeRegisterElement(xmlElement);
        Document document = new Document();
        document.setValue(documentBytes);
        document.setId(getDocumentId(request));
        request.getDocument().add(document);

        String taskId = UUID.randomUUID().toString();
        FileRawUpload fileUpload = new FileRawUpload(
            epaContext,
            ig,
            taskId,
            contentType,
            languageCode,
            telematikId,
            kvnr,
            fileName,
            "other",
            request,
            documentBytes
        );
        fileActionEvent.fireAsync(fileUpload);
        return taskId;
    }

    private String getDocumentId(ProvideAndRegisterDocumentSetRequestType request) {
        Optional<? extends IdentifiableType> extrinsicObjectTypeOpt = request.getSubmitObjectsRequest().getRegistryObjectList().getIdentifiable().stream().filter(idt -> {
            IdentifiableType identifiableType = idt.getValue();
            return identifiableType instanceof ExtrinsicObjectType;
        }).map(JAXBElement::getValue).findFirst();

        ExtrinsicObjectType extrinsicObjectType = (ExtrinsicObjectType) extrinsicObjectTypeOpt.get();
        return extrinsicObjectType.getId();
    }

    // Based on: https://github.com/gematik/api-ePA/blob/ePA-2.6/samples/ePA%201%20Beispielnachrichten%20PS%20-%20Konnektor/Requests/provideandregister.xml
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Task uuid"),
        @APIResponse(responseCode = "500", description = "Internal server error")
    })
    @POST
    @Consumes(MEDIA_TYPE_WILDCARD)
    @Produces(TEXT_PLAIN)
    @Path("upload")
    @Operation(summary = "Upload single document XML/PDF/etc to the XDS registry")
    public String upload(
        @Parameter(
            name = "Content-Type",
            description = "MIME type of the uploaded document",
            example = "application/pdf",
            in = HEADER,
            required = true
        )
        @HeaderParam(CONTENT_TYPE) String contentType,
        @Parameter(
            name = "Lang-Code",
            description = "Language code for the document",
            example = "de-DE",
            in = HEADER,
            required = true
        )
        @HeaderParam("Lang-Code") String languageCode,
        @Parameter(
            name = "File-Name",
            description = "Original filename of the document",
            example = "medical-report.pdf",
            in = HEADER
        )
        @HeaderParam("File-Name") String fileName,
        @Parameter(
            name = "Title",
            description = "XDS title of the document",
            example = "PDF-Arztbrief an  kv.digital-eArztbrief",
            in = HEADER
        )
        @HeaderParam("Title") String title,
        @Parameter(name = "Author-Institution", example = "Praxis Heider", in = HEADER)
        @HeaderParam("Author-Institution") String authorInstitution,
        @Parameter(
            name = "Author-Lanr",
            description = "https://de.wikipedia.org/wiki/Lebenslange_Arztnummer",
            example = "605095502",
            in = HEADER
        )
        @HeaderParam("Author-Lanr") String authorLanr,
        @Parameter(name = "Author-FirstName", description = "Doctor first name", in = HEADER)
        @HeaderParam("Author-FirstName") String authorFirstName,
        @Parameter(name = "Author-LastName", description = "Doctor last name", in = HEADER)
        @HeaderParam("Author-LastName") String authorLastName,
        @Parameter(name = "Author-Title", description = "Doctor title", example = "Dr. med.", in = HEADER)
        @HeaderParam("Author-Title") String authorTitle,
        @Deprecated
        @Parameter(name = "Praxis", example = "Arztpraxis", in = HEADER)
        @HeaderParam("Praxis") String praxis,
        @Deprecated
        @Parameter(name = "Fachrichtung", example = "Naturheilverfahren", in = HEADER)
        @HeaderParam("Fachrichtung") String practiceSetting,
        @Deprecated
        @Parameter(name = "Information", example = "Format aus MIME Type ableitbar", in = HEADER)
        @HeaderParam("Information") String information,
        @Deprecated
        @Parameter(name = "Information2", example = "Format aus MIME Type ableitbar", in = HEADER)
        @HeaderParam("Information2") String information2,
        @Parameter(
            name = "PracticeSettingClassification",
            example = "nodeRepresentation=AUGE; name=Augenheilkunde",
            in = HEADER
        )
        @HeaderParam("PracticeSettingClassification") List<String> practiceSettingClassification,
        @Parameter(
            name = "FacilityTypeCodeClassification",
            example = "nodeRepresentation=PRA; name=Arztpraxis",
            in = HEADER
        )
        @HeaderParam("FacilityTypeCodeClassification") List<String> facilityTypeCodeClassification,
        @Parameter(
            name = "ClassCodeClassification",
            example = "nodeRepresentation=ADM; name=Administratives Dokument",
            in = HEADER
        )
        @HeaderParam("ClassCodeClassification") List<String> classCodeClassification,
        @Parameter(
            name = "TypeCodeClassification",
            example = "nodeRepresentation=ADCH; name=Administrative Checklisten",
            in = HEADER
        )
        @HeaderParam("TypeCodeClassification") List<String> typeCodeClassification,
        @Parameter(
            name = X_KONNEKTOR,
            description = "IP of the target Konnektor (can be skipped for single-tenancy)",
            in = QUERY
        )
        @QueryParam(X_KONNEKTOR) String konnektor,
        @Parameter(name = KVNR, description = "Patient KVNR", in = QUERY, required = true)
        @QueryParam(KVNR) String kvnr,
        @Parameter(name = "ig", description = "IG schema name", in = QUERY)
        @QueryParam("ig") String ig,
        @Parameter(description = "Document to submit to the XDS registry")
        InputStream is
    ) throws Exception {
        EpaContext epaContext = prepareEpaContext(kvnr);
        epaContext.getXHeaders().put(UPLOAD_CONTENT_TYPE, contentType);
        if (fileName == null) {
            fileName = String.format("%s_%s.%s", kvnr, UUID.randomUUID(), getExtension(contentType));
        }

        List<CustomCodingScheme> customCodingSchemes = extractCustomCodingSchemes(
            practiceSettingClassification, facilityTypeCodeClassification, classCodeClassification, typeCodeClassification
        );

        String taskId = UUID.randomUUID().toString();
        FileUpload fileUpload = new FileUpload(
            epaContext,
            ig,
            taskId,
            contentType,
            languageCode,
            telematikId,
            kvnr,
            fileName,
            title,
            authorInstitution,
            authorLanr,
            authorFirstName,
            authorLastName,
            authorTitle,
            praxis,
            practiceSetting,
            information,
            information2,
            "other",
            customCodingSchemes,
            is.readAllBytes()
        );
        fileActionEvent.fireAsync(fileUpload);
        return taskId;
    }

    private List<CustomCodingScheme> extractCustomCodingSchemes(
        List<String> practiceSettingClassification,
        List<String> facilityTypeCodeClassification,
        List<String> classCodeClassification,
        List<String> typeCodeClassification
    ) {
        List<CustomCodingScheme> codingSchemes = new ArrayList<>();
        if (!practiceSettingClassification.isEmpty()) {
            codingSchemes.add(new CustomCodingScheme(PracticeSettingClassification, c12nHeaderToMap(practiceSettingClassification)));
        }
        if (!facilityTypeCodeClassification.isEmpty()) {
            codingSchemes.add(new CustomCodingScheme(FacilityTypeCodeClassification, c12nHeaderToMap(facilityTypeCodeClassification)));
        }
        if (!classCodeClassification.isEmpty()) {
            codingSchemes.add(new CustomCodingScheme(ClassCodeClassification, c12nHeaderToMap(classCodeClassification)));
        }
        if (!typeCodeClassification.isEmpty()) {
            codingSchemes.add(new CustomCodingScheme(TypeCodeClassification, c12nHeaderToMap(typeCodeClassification)));
        }
        return codingSchemes;
    }

    private Map<String, String> c12nHeaderToMap(List<String> c12nHeader) {
        return c12nHeader.stream()
            .flatMap(values -> Arrays.stream(values.split("[;,]")))
            .map(String::trim)
            .map(kv -> kv.split("="))
            .map(kvArr -> Pair.of(kvArr[0], kvArr[1]))
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    private String getExtension(String contentType) {
        if (isXmlCompliant(contentType)) {
            return "xml";
        } else if (isPdfCompliant(contentType)) {
            return "pdf";
        } else {
            return "dat";
        }
    }
}