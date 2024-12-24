package de.servicehealth.epa4all.server.rest;

import de.servicehealth.epa4all.server.filetracker.download.FileDownload;
import de.servicehealth.epa4all.server.filetracker.upload.FileUpload;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.xml.bind.JAXBElement;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryRequest;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.IdentifiableType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotType1;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.servicehealth.epa4all.xds.XDSUtils.isPdfCompliant;
import static de.servicehealth.epa4all.xds.XDSUtils.isXmlCompliant;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;

@RequestScoped
@Path("xds-document")
public class XdsDocument extends AbstractResource {

    @GET
    @Path("query/{konnektor : ([0-9a-zA-Z\\-]+)?}")
    public AdhocQueryResponse query(
        @PathParam("konnektor") String konnektor,
        @QueryParam("kvnr") String kvnr
    ) {
        try {
            EpaContext epaContext = prepareEpaContext(kvnr);
            return getAdhocQueryResponse(kvnr, epaContext);
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @GET
    @Path("downloadAll/{konnektor : ([0-9a-zA-Z\\-]+)?}")
    public String downloadAll(
        @PathParam("konnektor") String konnektor,
        @QueryParam("kvnr") String kvnr
    ) {
        try {
            EpaContext epaContext = prepareEpaContext(kvnr);
            AdhocQueryResponse adhocQueryResponse = getAdhocQueryResponse(kvnr, epaContext);
            List<JAXBElement<? extends IdentifiableType>> jaxbElements = adhocQueryResponse.getRegistryObjectList().getIdentifiable();
            List<String> tasksIds = bulkTransfer.downloadInsurantFiles(
                epaContext, telematikId, kvnr, jaxbElements
            );
            return String.join("\n", tasksIds);
        } catch (Exception e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }

    @GET
    @Path("document/{konnektor}/{uniqueId}")
    public RetrieveDocumentSetResponseType get(
        @PathParam("konnektor") String konnektor,
        @PathParam("uniqueId") String uniqueId,
        @QueryParam("kvnr") String kvnr
    ) {
        try {
            EpaContext epaContext = prepareEpaContext(kvnr);
            Optional<String> repositoryUniqueIdOpt = getAdhocQueryResponse(kvnr, epaContext).getRegistryObjectList().getIdentifiable()
                .stream()
                .filter(e -> {
                    Optional<SlotType1> fileNameOpt = e.getValue().getSlot().stream().filter(s -> s.getName().equals("URI")).findFirst();
                    return fileNameOpt.map(st -> st.getValueList().getValue().getFirst().contains(uniqueId)).isPresent();
                })
                .findFirst()
                .flatMap(e -> e.getValue().getSlot()
                    .stream()
                    .filter(s -> s.getName().equals("repositoryUniqueId"))
                    .findFirst().map(st -> st.getValueList().getValue().getFirst())
                );

            String repositoryUniqueId = repositoryUniqueIdOpt.orElse("undefined");
            IDocumentManagementPortType documentManagementPortType = epaFileDownloader.getDocumentManagementPortType(epaContext);
            RetrieveDocumentSetRequestType requestType = xdsDocumentService.get().prepareRetrieveDocumentSetRequestType(
                uniqueId, repositoryUniqueId
            );
            RetrieveDocumentSetResponseType response = documentManagementPortType.documentRepositoryRetrieveDocumentSet(requestType);
            handleDownloadResponse(response, uniqueId, epaContext, kvnr, repositoryUniqueId);
            return response;
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    // TODO refactor to complete event approach
    private void handleDownloadResponse(
        RetrieveDocumentSetResponseType response,
        String uniqueId,
        EpaContext epaContext,
        String kvnr,
        String repositoryUniqueId
    ) throws Exception {
        String taskId = UUID.randomUUID().toString();
        RetrieveDocumentSetResponseType.DocumentResponse documentResponse = response.getDocumentResponse().getFirst();
        String mimeType = documentResponse.getMimeType();
        String fileName = uniqueId;
        if (isXmlCompliant(mimeType)) {
            fileName = fileName + ".xml";
        } else if (isPdfCompliant(mimeType)) {
            fileName = fileName + ".pdf";
        }
        FileDownload fileDownload = new FileDownload(epaContext, taskId, fileName, telematikId, kvnr, repositoryUniqueId);
        epaFileDownloader.handleDownloadResponse(taskId, fileDownload, response);
    }

    private AdhocQueryResponse getAdhocQueryResponse(String kvnr, EpaContext epaContext) {
        IDocumentManagementPortType documentManagementPortType = epaFileDownloader.getDocumentManagementPortType(epaContext);
        AdhocQueryRequest request = xdsDocumentService.get().prepareAdhocQueryRequest(kvnr);
        return documentManagementPortType.documentRegistryRegistryStoredQuery(request);
    }

    @GET
    @Produces(MediaType.APPLICATION_XML)
    @Path("result/{taskId}")
    public RegistryResponseType getUploadResult(@PathParam("taskId") String taskId) {
        return epaFileDownloader.getResult(taskId);
    }

    // Based on: https://github.com/gematik/api-ePA/blob/ePA-2.6/samples/ePA%201%20Beispielnachrichten%20PS%20-%20Konnektor/Requests/provideandregister.xml
    @POST
    @Consumes(MediaType.MEDIA_TYPE_WILDCARD)
    @Produces(MediaType.TEXT_PLAIN)
    @Path("{konnektor : ([0-9a-zA-Z\\-]+)?}")
    public String post(
        @HeaderParam(CONTENT_TYPE) String contentType,
        @HeaderParam("Lang-Code") String languageCode,
        @HeaderParam("File-Name") String fileName,
        @PathParam("konnektor") String konnektor,
        @QueryParam("kvnr") String kvnr,
        InputStream is
    ) {
        try {
            EpaContext epaContext = prepareEpaContext(kvnr);

            if (fileName == null) {
                fileName = String.format("%s_%s.%s", kvnr, UUID.randomUUID(), getExtension(contentType));
            }

            String folderName = null;

            byte[] documentBytes = is.readAllBytes();
            String taskId = UUID.randomUUID().toString();
            eventFileUpload.fireAsync(new FileUpload(
                epaContext, taskId, contentType, languageCode, telematikId, kvnr, fileName, folderName, documentBytes
            ));
            return taskId;
        } catch (Exception e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
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
