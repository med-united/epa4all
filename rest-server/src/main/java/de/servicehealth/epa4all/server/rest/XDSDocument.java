package de.servicehealth.epa4all.server.rest;

import de.service.health.api.epa4all.EpaAPI;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType.DocumentRequest;
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
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryRequest;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import oasis.names.tc.ebxml_regrep.xsd.query._3.ResponseOptionType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.AdhocQueryType;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;

import java.io.InputStream;

import static de.servicehealth.epa4all.xds.XDSUtils.createSlotType;

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
            EpaAPI epaAPI = xdsDocumentService.getEpaInsurantPair(telematikId, kvnr, smcbHandle, userRuntimeConfig).getLeft();

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

            return epaAPI.getDocumentManagementPortType().documentRegistryRegistryStoredQuery(adhocQueryRequest);
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @GET
    @Path("document/{konnektor : ([0-9a-zA-Z\\-]+)?}/{uniqueId : (/[0-9a-zA-Z\\-]+)?}")
    public RetrieveDocumentSetResponseType get(
        @PathParam("konnektor") String konnektor,
        @PathParam("uniqueId") String uniqueId,
        @QueryParam("kvnr") String kvnr
    ) {
        try {
            EpaAPI epaAPI = xdsDocumentService.getEpaInsurantPair(telematikId, kvnr, smcbHandle, userRuntimeConfig).getLeft();

            RetrieveDocumentSetRequestType retrieveDocumentSetRequestType = new RetrieveDocumentSetRequestType();
            DocumentRequest documentRequest = new DocumentRequest();
            documentRequest.setDocumentUniqueId(uniqueId);
            documentRequest.setRepositoryUniqueId("1.2.276.0.76.3.1.315.3.2.1.1");
            retrieveDocumentSetRequestType.getDocumentRequest().add(documentRequest);
            return epaAPI.getDocumentManagementPortType().documentRepositoryRetrieveDocumentSet(retrieveDocumentSetRequestType);
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    // Based on: https://github.com/gematik/api-ePA/blob/ePA-2.6/samples/ePA%201%20Beispielnachrichten%20PS%20-%20Konnektor/Requests/provideandregister.xml
    @POST
    @Consumes(MediaType.MEDIA_TYPE_WILDCARD)
    @Produces(MediaType.APPLICATION_XML)
    @Path("{konnektor : ([0-9a-zA-Z\\-]+)?}")
    public RegistryResponseType post(
        @HeaderParam("Content-Type") String contentType,
        @HeaderParam("Lang-Code") String languageCode,
        @PathParam("konnektor") String konnektor,
        @QueryParam("kvnr") String kvnr,
        InputStream is
    ) {
        try {
            return xdsDocumentService.uploadXDSDocument(
                kvnr,
                telematikId,
                smcbHandle,
                contentType,
                languageCode,
                userRuntimeConfig,
                is.readAllBytes()
            );
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }
}
