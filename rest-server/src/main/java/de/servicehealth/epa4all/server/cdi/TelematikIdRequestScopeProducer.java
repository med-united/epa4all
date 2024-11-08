package de.servicehealth.epa4all.server.cdi;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.domain.eventservice.card.Card;
import de.health.service.cetp.domain.fault.CetpFault;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.smcb.WebdavSmcbManager;
import de.servicehealth.epa4all.xds.ProvideAndRegisterSingleDocumentTypeBuilder;
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
    WebdavSmcbManager smcbManager;

    @Inject
    IKonnektorClient konnektorClient;

    @Inject
    DefaultUserConfig defaultUserConfig;

    @JsonInclude
    @Produces
    @TelematikId
    public String telematikId() {
        try {
            List<Card> cards = konnektorClient.getCards(defaultUserConfig, SMC_B);
            Optional<Card> vizenzkrCard = cards.stream().filter(c -> "VincenzkrankenhausTEST-ONLY".equals(c.getCardHolderName())).findAny();
            String smcbHandle;
            if (vizenzkrCard.isPresent()) {
                smcbHandle = vizenzkrCard.get().getCardHandle();
            } else {
                smcbHandle = cards.getFirst().getCardHandle();
            }

            Pair<X509Certificate, Boolean> x509Certificate = konnektorClient.getSmcbX509Certificate(defaultUserConfig, smcbHandle);
            return smcbManager.extractTelematikIdFromCertificate(x509Certificate.getKey());
        } catch (CetpFault e) {
            throw new RuntimeException(e);
        }
    }
}
