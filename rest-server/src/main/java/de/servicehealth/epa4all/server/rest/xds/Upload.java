package de.servicehealth.epa4all.server.rest.xds;

import de.servicehealth.epa4all.server.filetracker.upload.FileRawUpload;
import de.servicehealth.epa4all.server.filetracker.upload.FileUpload;
import de.servicehealth.epa4all.server.rest.EpaContext;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType.Document;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.xml.bind.JAXBElement;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.IdentifiableType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import static de.servicehealth.epa4all.server.filetracker.upload.soap.RawSoapUtils.deserializeUploadRequest;
import static de.servicehealth.epa4all.server.rest.xds.XdsResource.XDS_DOCUMENT_PATH;
import static de.servicehealth.epa4all.xds.XDSUtils.isPdfCompliant;
import static de.servicehealth.epa4all.xds.XDSUtils.isXmlCompliant;
import static de.servicehealth.vau.VauClient.KVNR;
import static de.servicehealth.vau.VauClient.UPLOAD_CONTENT_TYPE;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
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
        MultipartFormDataInput input
    ) throws Exception {
        EpaContext epaContext = prepareEpaContext(kvnr);
        epaContext.getXHeaders().put(UPLOAD_CONTENT_TYPE, contentType);

        InputPart bytesPart = input.getFormDataMap().get("pdf_body").getFirst();
        String fileName = bytesPart.getFileName();
        InputStream is = bytesPart.getBody(InputStream.class, null);
        byte[] documentBytes = is.readAllBytes();

        InputPart soapPart = input.getFormDataMap().get("raw_soap").getFirst();
        String rawSoapRequest = soapPart.getBody(String.class, null);
        ProvideAndRegisterDocumentSetRequestType request = deserializeUploadRequest(rawSoapRequest);
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
    @Consumes(MediaType.MEDIA_TYPE_WILDCARD)
    @Produces(MediaType.TEXT_PLAIN)
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
        @Parameter(name = "Praxis", example = "Arztpraxis", in = HEADER)
        @HeaderParam("Praxis") String praxis,
        @Parameter(name = "Fachrichtung", example = "Naturheilverfahren", in = HEADER)
        @HeaderParam("Fachrichtung") String practiceSetting,
        @Parameter(name = "Information", example = "Format aus MIME Type ableitbar", in = HEADER)
        @HeaderParam("Information") String information,
        @Parameter(name = "Information2", example = "Format aus MIME Type ableitbar", in = HEADER)
        @HeaderParam("Information2") String information2,
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
        @Parameter(description = "Document to submit to the XDS registry", example = "xml/pdf")
        InputStream is
    ) throws Exception {
        EpaContext epaContext = prepareEpaContext(kvnr);
        epaContext.getXHeaders().put(UPLOAD_CONTENT_TYPE, contentType);
        if (fileName == null) {
            fileName = String.format("%s_%s.%s", kvnr, UUID.randomUUID(), getExtension(contentType));
        }
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
            authorLanr,
            authorFirstName,
            authorLastName,
            authorTitle,
            praxis,
            practiceSetting,
            information,
            information2,
            "other",
            is.readAllBytes()
        );
        fileActionEvent.fireAsync(fileUpload);
        return taskId;
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