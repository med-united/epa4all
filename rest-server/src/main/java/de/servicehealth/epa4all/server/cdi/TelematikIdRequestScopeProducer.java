package de.servicehealth.epa4all.server.cdi;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.domain.eventservice.card.Card;
import de.health.service.cetp.domain.fault.CetpFault;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.smcb.WebdavSmcbManager;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

import static de.health.service.cetp.domain.eventservice.card.CardType.SMC_B;

@RequestScoped
public class TelematikIdRequestScopeProducer {

    @Inject
    IKonnektorClient konnektorClient;

    @Inject
    UserRuntimeConfig userRuntimeConfig;

    String smcbHandle;

    @Produces
    @TelematikId
    public String telematikId() {
        try {
            initSMCBHandle();
            Pair<X509Certificate, Boolean> x509Certificate = konnektorClient.getSmcbX509Certificate(userRuntimeConfig, smcbHandle);

            return WebdavSmcbManager.extractTelematikIdFromCertificate(x509Certificate.getKey());
        } catch (CetpFault e) {
            throw new RuntimeException(e);
        }
    }

    @Produces
    @SMCBHandle
    public String smcbHandle() {
        try {
            initSMCBHandle();
            return smcbHandle;
        } catch (CetpFault e) {
            throw new RuntimeException(e);
        }
    }

    public void initSMCBHandle() throws CetpFault {
        if (smcbHandle == null) {
            List<Card> cards = konnektorClient.getCards(userRuntimeConfig, SMC_B);

            Optional<Card> vizenzkrCard = cards.stream().filter(c -> "Praxis SigmuntowskíTEST-ONLY".equals(c.getCardHolderName())).findAny();
            if (vizenzkrCard.isPresent()) {
                smcbHandle = vizenzkrCard.get().getCardHandle();
            } else {
                smcbHandle = cards.getFirst().getCardHandle();
            }
        }
    }
}
