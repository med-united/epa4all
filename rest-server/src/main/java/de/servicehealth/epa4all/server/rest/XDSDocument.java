package de.servicehealth.epa4all.server.rest;

import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.service.health.api.epa4all.EpaAPI;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
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
import jakarta.xml.ws.BindingProvider;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryRequest;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import oasis.names.tc.ebxml_regrep.xsd.query._3.ResponseOptionType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.AdhocQueryType;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

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
            String correlationId = UUID.randomUUID().toString();

            InsuranceData insuranceData = insuranceDataService.getInsuranceData(
                telematikId, kvnr, correlationId, smcbHandle, userRuntimeConfig
            );
            EpaAPI epaAPI = xdsDocumentService.setEntitlementAndGetEpaAPI(userRuntimeConfig, insuranceData, smcbHandle);


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

            IDocumentManagementPortType documentManagementPortType = epaAPI.getDocumentManagementPortType();
            attachVauAttributes((BindingProvider) documentManagementPortType, insuranceData);
            return documentManagementPortType.documentRegistryRegistryStoredQuery(adhocQueryRequest);
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
            String correlationId = UUID.randomUUID().toString();
            InsuranceData insuranceData = insuranceDataService.getInsuranceData(
                telematikId, kvnr, correlationId, smcbHandle, userRuntimeConfig
            );
            EpaAPI epaAPI = xdsDocumentService.setEntitlementAndGetEpaAPI(userRuntimeConfig, insuranceData, smcbHandle);

            RetrieveDocumentSetRequestType retrieveDocumentSetRequestType = new RetrieveDocumentSetRequestType();
            DocumentRequest documentRequest = new DocumentRequest();
            documentRequest.setDocumentUniqueId(uniqueId);
            documentRequest.setRepositoryUniqueId("1.2.276.0.76.3.1.315.3.2.1.1");
            retrieveDocumentSetRequestType.getDocumentRequest().add(documentRequest);
            IDocumentManagementPortType documentManagementPortType = epaAPI.getDocumentManagementPortType();
            attachVauAttributes((BindingProvider) documentManagementPortType, insuranceData);
            return documentManagementPortType.documentRepositoryRetrieveDocumentSet(retrieveDocumentSetRequestType);
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
            String correlationId = UUID.randomUUID().toString();

            InsuranceData insuranceData = insuranceDataService.getInsuranceData(
                telematikId, kvnr, correlationId, smcbHandle, userRuntimeConfig
            );
            EpaAPI epaAPI = xdsDocumentService.setEntitlementAndGetEpaAPI(userRuntimeConfig, insuranceData, smcbHandle);

            UCPersoenlicheVersichertendatenXML versichertendaten = insuranceData.getPersoenlicheVersichertendaten();
            UCPersoenlicheVersichertendatenXML.Versicherter.Person person = versichertendaten.getVersicherter().getPerson();
            String firstName = person.getVorname();
            String lastName = person.getNachname();
            String title = person.getTitel();

            ProvideAndRegisterDocumentSetRequestType request = xdsDocumentService.prepareDocumentSetRequest(
                is.readAllBytes(), telematikId, kvnr, contentType, languageCode, firstName, lastName, title
            );
            IDocumentManagementPortType documentManagementPortType = epaAPI.getDocumentManagementPortType();
            attachVauAttributes((BindingProvider) documentManagementPortType, insuranceData);
            return documentManagementPortType.documentRepositoryProvideAndRegisterDocumentSetB(request);
        } catch (Exception e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }

    private void attachVauAttributes(BindingProvider bindingProvider, InsuranceData insuranceData) throws Exception {
        Map<String, Object> runtimeAttributes = prepareRuntimeAttributes(insuranceData);
        bindingProvider.getRequestContext().putAll(runtimeAttributes);
    }
}
