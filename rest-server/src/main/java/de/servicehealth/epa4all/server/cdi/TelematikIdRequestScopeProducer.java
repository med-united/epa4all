package de.servicehealth.epa4all.server.cdi;

import static de.health.service.cetp.domain.eventservice.card.CardType.SMC_B;

import java.security.cert.X509Certificate;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.cetp.domain.eventservice.card.Card;
import de.health.service.cetp.domain.fault.CetpFault;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.serviceport.MultiKonnektorService;
import de.servicehealth.epa4all.server.smcb.WebdavSmcbManager;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@RequestScoped
public class TelematikIdRequestScopeProducer {
	
	@Inject
    MultiKonnektorService multiKonnektorService;
	
	@Inject
    IKonnektorClient konnektorClient;
	
	@Inject
	KonnektorDefaultConfig konnektorDefaultConfig;
	
	@Inject
	DefaultUserConfig defaultUserConfig;
	
	@Produces
	@TelematikId
	public String telematikId() {
		try {
			List<Card> cards = konnektorClient.getCards(defaultUserConfig, SMC_B);
	        String smcbHandle = cards.getFirst().getCardHandle();
	        Pair<X509Certificate, Boolean> x509Certificate = konnektorClient.getSmcbX509Certificate(defaultUserConfig, smcbHandle);
	        
	        return WebdavSmcbManager.extractTelematikIdFromCertificate(x509Certificate.getKey());
		} catch (CetpFault e) {
			throw new RuntimeException(e);
		}
	}
}
