package de.servicehealth.epa4all.server.rest;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.server.cdi.TelematikId;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.vsds.VSDService;
import de.servicehealth.epa4all.xds.structure.StructureDefinitionService;
import de.servicehealth.model.EntitlementRequestType;
import de.servicehealth.model.ValidToResponseType;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.cxf.client.ClientFactory.USER_AGENT;

public abstract class AbstractResource {

	private static final Logger log = Logger.getLogger(AbstractResource.class.getName());

	@Inject
	VSDService vsdService;

	@Inject
	StructureDefinitionService structureDefinitionService;
	
	@Inject
	DefaultUserConfig defaultUserConfig;

	@Inject
	MultiEpaService multiEpaService;

	@Inject
	IdpClient idpClient;

	@Inject
	@TelematikId
	String telematikId;

	public EpaAPI initAndGetEpaAPI(String konnektor, String egkHandle) throws Exception, IOException, SAXException {
		ReadVSDResponse readVSDResponse = vsdService.readVSD(konnektor, egkHandle, null, defaultUserConfig);
		Document doc = VSDService.createDocument(readVSDResponse);
		String xInsurantid = VSDService.getKVNRFromResponseOrDoc(readVSDResponse, doc);
		multiEpaService.setXInsurantid(xInsurantid);
		EpaAPI epaAPI = multiEpaService.getEpaAPI();
		if (epaAPI == null) {
			throw new WebApplicationException("No epa found for: " + xInsurantid);
		}
		String np = idpClient.getVauNpSync(defaultUserConfig);
		epaAPI.setNp(np);

		EntitlementRequestType entitlementRequest = new EntitlementRequestType();
		String pz = doc.getElementsByTagName("PZ").item(0).getTextContent();
		String entitlementPSJWT = idpClient.createEntitlementPSJWT(pz, defaultUserConfig);
		entitlementRequest.setJwt(entitlementPSJWT);
		ValidToResponseType response = epaAPI.getEntitlementsApi().setEntitlementPs(xInsurantid, USER_AGENT,
				entitlementRequest);
		log.info(response.toString());
		return epaAPI;
	}

}
