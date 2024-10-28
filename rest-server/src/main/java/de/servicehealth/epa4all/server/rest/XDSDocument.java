package de.servicehealth.epa4all.server.rest;

import java.nio.file.Files;
import java.util.stream.Collectors;

import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.vsds.VSDService;
import ihe.iti.xds_b._2007.IDocumentManagementPortType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType.Document;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType.DocumentRequest;
import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.WebApplicationException;
import oasis.names.tc.ebxml_regrep.xsd.lcm._3.SubmitObjectsRequest;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryRequest;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.AdhocQueryType;


@Path("xds-document")
public class XDSDocument {
	
	@Inject
	VSDService pharmacyService;
	
	@Inject
	DefaultUserConfig defaultUserConfig;
	
	@Inject
    MultiEpaService multiEpaService;

	@GET
	@Path("{konnektor : (\\w+)?}{egkHandle : (/\\w+)?}")
	public String get(@PathParam("konnektor") String konnektor, @PathParam("egkHandle") String egkHandle) {
		try {
			String xInsurantid = pharmacyService.getKVNR(konnektor, egkHandle, null, defaultUserConfig);
			EpaAPI epaAPI = multiEpaService.getEpaAPI(xInsurantid);
			
			if(epaAPI == null) {
				return "No epa found for: "+xInsurantid;
			}
			IDocumentManagementPortType documentManagementPortType = epaAPI.getDocumentManagementPortType();
			AdhocQueryRequest adhocQueryRequest = new AdhocQueryRequest();
			AdhocQueryType adhocQueryType = new AdhocQueryType();
			// Find document by title: urn:uuid:ab474085-82b5-402d-8115-3f37cb1e2405
			String documentByTitle = "urn:uuid:ab474085-82b5-402d-8115-3f37cb1e2405";
			String documentByComment = "urn:uuid:2609dda5-2b97-44d5-a795-3e999c24ca99";
			adhocQueryType.setId(documentByTitle);
			adhocQueryRequest.setAdhocQuery(adhocQueryType);
			AdhocQueryResponse adhocQueryResponse = documentManagementPortType.documentRegistryRegistryStoredQuery(adhocQueryRequest);
			
			RetrieveDocumentSetRequestType retrieveDocumentSetRequestType = new RetrieveDocumentSetRequestType();
			DocumentRequest documentRequest = new DocumentRequest();
			// documentRequest.setDocumentUniqueId("");
			// documentRequest.setHomeCommunityId("");
			// documentRequest.setRepositoryUniqueId("");
			retrieveDocumentSetRequestType.getDocumentRequest().add(documentRequest);
			RetrieveDocumentSetResponseType retrieveDocumentSetResponseType = documentManagementPortType.documentRepositoryRetrieveDocumentSet(retrieveDocumentSetRequestType);
			String documentIds = retrieveDocumentSetResponseType.getDocumentResponse().stream().map(d -> d.getDocumentUniqueId()).collect(Collectors.joining(", "));
			
			ProvideAndRegisterDocumentSetRequestType provideAndRegisterDocumentSetRequestType = new ProvideAndRegisterDocumentSetRequestType();
			Document document = new Document();
			document.setId("2ed6995a-ddcb-4af9-af62-80dac413408b");
			document.setValue(getClass().getResourceAsStream("/test-data/EEAU0_2ed6995a-ddcb-4af9-af62-80dac413408b.xml").readAllBytes());
			provideAndRegisterDocumentSetRequestType.getDocument().add(document);
			SubmitObjectsRequest submitObjectsRequest = new SubmitObjectsRequest();
			submitObjectsRequest.setComment("EAU: 2ed6995a-ddcb-4af9-af62-80dac413408b");
			provideAndRegisterDocumentSetRequestType.setSubmitObjectsRequest(submitObjectsRequest);
			
			documentManagementPortType.documentRepositoryProvideAndRegisterDocumentSetB(provideAndRegisterDocumentSetRequestType);
			return documentIds;
		} catch (Exception e) {
			throw new WebApplicationException(e);
		}
		
	}
}
