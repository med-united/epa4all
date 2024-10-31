package de.servicehealth.epa4all.server.rest;

import java.io.IOException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.idp.IdpClient;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.vsds.VSDService;
import de.servicehealth.model.EntitlementRequestType;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

public abstract class AbstractResource {
	
	@Inject
	VSDService vsdService;
	
	@Inject
	DefaultUserConfig defaultUserConfig;
	
	@Inject
    MultiEpaService multiEpaService;
	
	@Inject
	IdpClient idpClient;

	public EpaAPI initAndGetEpaAPI(String konnektor, String egkHandle) throws Exception, IOException, SAXException {
		ReadVSDResponse readVSDResponse = vsdService.readVSD(konnektor, egkHandle, null, defaultUserConfig);
		Document doc = VSDService.createDocument(readVSDResponse);
		String xInsurantid = VSDService.getKVNRFromDocument(doc);
		multiEpaService.setXInsurantid(xInsurantid);
		EpaAPI epaAPI = multiEpaService.getEpaAPI();
		if (epaAPI == null) {
			throw new WebApplicationException("No epa found for: " + xInsurantid);
		}
		String np = idpClient.getVauNpSync(defaultUserConfig);
		epaAPI.setNp(np);
		
		EntitlementRequestType entitlementRequest = new EntitlementRequestType();
		String pz = doc.getElementsByTagName("PZ").item(0).getTextContent();
		entitlementRequest.setJwt(idpClient.createEntitilementPSJWT(pz, defaultUserConfig));
		epaAPI.getEntitlementsApi().setEntitlementPs(xInsurantid, "CLIENTID", entitlementRequest);
		return epaAPI;
	}

}
