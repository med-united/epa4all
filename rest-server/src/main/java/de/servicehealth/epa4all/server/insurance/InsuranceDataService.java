package de.servicehealth.epa4all.server.insurance;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.domain.eventservice.card.Card;
import de.health.service.cetp.domain.eventservice.card.CardType;
import de.health.service.cetp.domain.fault.CetpFault;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.smcb.WebdavSmcbManager;
import de.servicehealth.epa4all.server.vsd.VSDService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.xml.bind.DatatypeConverter;
import org.apache.commons.lang3.tuple.Pair;
import org.w3c.dom.Document;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.server.insurance.InsuranceXmlUtils.createDocument;
import static de.servicehealth.epa4all.server.insurance.InsuranceXmlUtils.createUCDocument;

@ApplicationScoped
public class InsuranceDataService {

    private static final Logger log = Logger.getLogger(InsuranceDataService.class.getName());

    private final WebdavSmcbManager webdavSmcbManager;
    private final IKonnektorClient konnektorClient;
    private final VSDService vsdService;

    @Inject
    Event<ReadVSDResponseEx> readVSDResponseExEvent;

    @Inject
    public InsuranceDataService(
        WebdavSmcbManager webdavSmcbManager,
        IKonnektorClient konnektorClient,
        VSDService vsdService
    ) {
        this.webdavSmcbManager = webdavSmcbManager;
        this.konnektorClient = konnektorClient;
        this.vsdService = vsdService;
    }

    // TODO check if it is possible to get KVNR by egkHandle

    public String getEGKHandle(UserRuntimeConfig userRuntimeConfig, String insurantId) {
        try {
            List<Card> cards = konnektorClient.getCards(userRuntimeConfig, CardType.EGK);
            Optional<Card> card = cards.stream().filter(c -> c.getKvnr().equals(insurantId)).findAny();
            return card.isPresent() ? card.get().getCardHandle() : cards.getFirst().getCardHandle();
        } catch (CetpFault e) {
            log.log(Level.SEVERE, "Could not get card for insurantId: " + insurantId, e);
        }
        return null;
    }

    public InsuranceData getInsuranceData(
        String telematikId,
        String kvnr,
        String correlationId,
        String egkHandle,
        String smcbHandle,
        UserRuntimeConfig runtimeConfig
    ) throws Exception {
        InsuranceData localInsuranceData = webdavSmcbManager.getLocalInsuranceData(telematikId, kvnr);
        if (localInsuranceData != null) {
            return localInsuranceData;
        }
        ReadVSDResponse readVSDResponse = vsdService.readVSD(correlationId, egkHandle, smcbHandle, runtimeConfig);
        String xInsurantId = extractInsurantId(readVSDResponse);
        if (kvnr == null || kvnr.equals(xInsurantId)) {
            // ReadVSDResponseEx must be sent synchronously to get valid local InsuranceData.
            readVSDResponseExEvent.fire(new ReadVSDResponseEx(telematikId, xInsurantId, readVSDResponse));
            return webdavSmcbManager.getLocalInsuranceData(telematikId, xInsurantId);
        }
        throw new IllegalStateException(String.format("Mismatch found: KVNR=%s, xInsurantId=%s", kvnr, xInsurantId));
    }

    private String extractInsurantId(ReadVSDResponse readVSDResponse) throws Exception {
        Document doc = createDocument(readVSDResponse.getPruefungsnachweis());
        String e = doc.getElementsByTagName("E").item(0).getTextContent();

        if (e.equals("3")) {
            byte[] bytes = readVSDResponse.getPersoenlicheVersichertendaten();
            UCPersoenlicheVersichertendatenXML patient = createUCDocument(bytes);
            String versichertenId = patient.getVersicherter().getVersichertenID();
            log.fine("VSDM result: " + e + " VersichertenID: " + versichertenId);
            return versichertenId;
        } else {
            String pz = doc.getElementsByTagName("PZ").item(0).getTextContent();
            byte[] base64Binary = DatatypeConverter.parseBase64Binary(pz);
            String base64PN = new String(base64Binary);
            return base64PN.substring(0, 10);
        }
    }
}
