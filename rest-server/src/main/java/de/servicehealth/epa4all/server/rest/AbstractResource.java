package de.servicehealth.epa4all.server.rest;

import static de.servicehealth.epa4all.cxf.client.ClientFactory.USER_AGENT;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.domain.eventservice.card.Card;
import de.health.service.cetp.domain.eventservice.card.CardType;
import de.health.service.cetp.domain.fault.CetpFault;
import de.health.service.config.api.UserRuntimeConfig;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.server.cdi.FromHttpPath;
import de.servicehealth.epa4all.server.cdi.TelematikId;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.vsds.VSDService;
import de.servicehealth.model.EntitlementRequestType;
import de.servicehealth.model.ValidToResponseType;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

public abstract class AbstractResource {

	private static final Logger log = Logger.getLogger(AbstractResource.class.getName());

	@Inject
	VSDService vsdService;

	@Inject
	@FromHttpPath
	UserRuntimeConfig userRuntimeConfig;

	@Inject
	MultiEpaService multiEpaService;

	@Inject
	IdpClient idpClient;

	@Inject
	IKonnektorClient konnektorClient;
	
	@Inject
	@TelematikId
	String telematikId;
	

	public String getEGKHandle(String egkHandle, String kvnr) {
		if (egkHandle != null) {
		    egkHandle = egkHandle.replaceAll("/", "");
		} else if(kvnr != null) {
			egkHandle = getCardHandleForKvnr(kvnr);
		}
		return egkHandle;
	}
	
	public String getCardHandleForKvnr(String kvnr) {
		List<Card> cards;
		try {
			cards = konnektorClient.getCards(userRuntimeConfig, CardType.EGK);
			Optional<Card> card = cards.stream().filter(c -> c.getKvnr().equals(kvnr)).findAny();
			if(card.isPresent()) {				
				return card.get().getCardHandle();
			}
		} catch (CetpFault e) {
			log.log(Level.SEVERE, "Could not get card for kvnr: "+kvnr, e);
		}
		return null;
	}
	
	public EpaAPI initAndGetEpaAPI(String konnektor, String egkHandle) throws Exception, IOException, SAXException {
		return initAndGetEpaAPI(konnektor, egkHandle, null);
	}

	public EpaAPI initAndGetEpaAPI(String konnektor, String egkHandle, String kvnr) throws Exception, IOException, SAXException {
		String xInsurantid;
		Document doc = null;
		if(kvnr != null) {
			xInsurantid = kvnr;
		} else {
			ReadVSDResponse readVSDResponse = vsdService.readVSD(konnektor, egkHandle, null, userRuntimeConfig);
			doc = VSDService.createDocument(readVSDResponse);
			xInsurantid = VSDService.getKVNRFromResponseOrDoc(readVSDResponse, doc);
		}
		multiEpaService.setXInsurantid(xInsurantid);
		EpaAPI epaAPI = multiEpaService.getEpaAPI();
		if (epaAPI == null) {
			throw new WebApplicationException("No epa found for: " + xInsurantid);
		}
		String np = idpClient.getVauNpSync(userRuntimeConfig);
		epaAPI.setNp(np);
		if(doc != null) {
			EntitlementRequestType entitlementRequest = new EntitlementRequestType();
			String pz = doc.getElementsByTagName("PZ").item(0).getTextContent();
			String entitlementPSJWT = idpClient.createEntitlementPSJWT(pz, userRuntimeConfig);
			entitlementRequest.setJwt(entitlementPSJWT);
			ValidToResponseType response = epaAPI.getEntitlementsApi().setEntitlementPs(xInsurantid, USER_AGENT,
					entitlementRequest);
			log.info(response.toString());
		}
		return epaAPI;
	}

}
