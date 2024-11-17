package de.servicehealth.epa4all.server.rest;

import de.service.health.api.epa4all.EpaAPI;
import de.servicehealth.epa4all.server.filetracker.FileUpload;
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
import jakarta.xml.ws.BindingProvider;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryRequest;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import static de.servicehealth.epa4all.xds.XDSUtils.isPdfCompliant;
import static de.servicehealth.epa4all.xds.XDSUtils.isXmlCompliant;

@RequestScoped
@Path("xds-document")
public class XDSDocument extends AbstractResource {

    @GET
    @Path("query/{konnektor : ([0-9a-zA-Z\\-]+)?}")
    public AdhocQueryResponse query(
        @PathParam("konnektor") String konnektor,
        @QueryParam("kvnr") String kvnr
    ) {
        try {
            EpaContext epaContext = prepareEpaContext(kvnr);
            EpaAPI epaAPI = multiEpaService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());
            IDocumentManagementPortType documentManagementPortType = epaAPI.getDocumentManagementPortType();
            attachVauAttributes((BindingProvider) documentManagementPortType, epaContext.getRuntimeAttributes());

            AdhocQueryRequest request = xdsDocumentService.get().prepareAdhocQueryRequest(kvnr);
            return documentManagementPortType.documentRegistryRegistryStoredQuery(request);
        } catch (Exception e) {
            throw new WebApplicationException(e);
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
            EpaAPI epaAPI = multiEpaService.getEpaAPI(epaContext.getInsuranceData().getInsurantId());
            IDocumentManagementPortType documentManagementPortType = epaAPI.getDocumentManagementPortType();
            attachVauAttributes((BindingProvider) documentManagementPortType, epaContext.getRuntimeAttributes());
            
            RetrieveDocumentSetRequestType requestType = xdsDocumentService.get().prepareRetrieveDocumentSetRequestType(uniqueId);
            return documentManagementPortType.documentRepositoryRetrieveDocumentSet(requestType);
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_XML)
    @Path("result/{taskId}")
    public RegistryResponseType getUploadResult(@PathParam("taskId") String taskId) {
        return epaFileTracker.getResult(taskId);
    }

    // Based on: https://github.com/gematik/api-ePA/blob/ePA-2.6/samples/ePA%201%20Beispielnachrichten%20PS%20-%20Konnektor/Requests/provideandregister.xml
    @POST
    @Consumes(MediaType.MEDIA_TYPE_WILDCARD)
    @Produces(MediaType.TEXT_PLAIN)
    @Path("{konnektor : ([0-9a-zA-Z\\-]+)?}")
    public String post(
        @HeaderParam("Content-Type") String contentType,
        @HeaderParam("Lang-Code") String languageCode,
        @PathParam("konnektor") String konnektor,
        @QueryParam("kvnr") String kvnr,
        InputStream is
    ) {
        try {
            EpaContext epaContext = prepareEpaContext(kvnr);

            String fileName = UUID.randomUUID() + "." + getExtension(contentType); // TODO get fileName
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

    private void attachVauAttributes(BindingProvider bindingProvider, Map<String, Object> runtimeAttributes) {
        bindingProvider.getRequestContext().putAll(runtimeAttributes);
    }
}
