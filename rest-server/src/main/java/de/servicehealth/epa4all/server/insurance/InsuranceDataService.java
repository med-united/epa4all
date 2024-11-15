package de.servicehealth.epa4all.server.insurance;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.domain.eventservice.card.Card;
import de.health.service.cetp.domain.eventservice.card.CardType;
import de.health.service.cetp.domain.fault.CetpFault;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.smcb.WebdavSmcbManager;
import de.servicehealth.epa4all.server.vsd.VSDService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.xml.bind.DatatypeConverter;
import org.apache.commons.lang3.tuple.Pair;
import org.w3c.dom.Document;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.health.service.cetp.domain.eventservice.card.CardType.SMC_B;
import static de.servicehealth.epa4all.server.insurance.InsuranceXmlUtils.createDocument;
import static de.servicehealth.epa4all.server.insurance.InsuranceXmlUtils.createUCEntity;
import static de.servicehealth.utils.SSLUtils.extractTelematikIdFromCertificate;

@ApplicationScoped
public class InsuranceDataService {

    private static final Logger log = Logger.getLogger(InsuranceDataService.class.getName());

    private final WebdavSmcbManager webdavSmcbManager;
    private final IKonnektorClient konnektorClient;
    private final FolderService folderService;
    private final VSDService vsdService;

    @Inject
    Event<ReadVSDResponseEx> readVSDResponseExEvent;

    @Inject
    public InsuranceDataService(
        WebdavSmcbManager webdavSmcbManager,
        IKonnektorClient konnektorClient,
        FolderService folderService,
        VSDService vsdService
    ) {
        this.webdavSmcbManager = webdavSmcbManager;
        this.konnektorClient = konnektorClient;
        this.folderService = folderService;
        this.vsdService = vsdService;
    }

    // TODO check if it is possible to get KVNR by egkHandle

    public String getEgkHandle(UserRuntimeConfig userRuntimeConfig, String insurantId) {
        try {
            List<Card> cards = konnektorClient.getCards(userRuntimeConfig, CardType.EGK);
            Optional<Card> card = cards.stream().filter(c -> c.getKvnr().equals(insurantId)).findAny();
            return card.map(Card::getCardHandle).orElse(cards.getFirst().getCardHandle());
        } catch (CetpFault e) {
            log.log(Level.SEVERE, "Could not get card for insurantId: " + insurantId, e);
        }
        return null;
    }

    public String getKvnr(UserRuntimeConfig userRuntimeConfig, String egkHandle) {
        try {
            List<Card> cards = konnektorClient.getCards(userRuntimeConfig, CardType.EGK);
            Optional<Card> cardOpt = cards.stream()
                .filter(c -> c.getCardHandle().equals(egkHandle))
                .findAny();
            return cardOpt.map(Card::getKvnr).orElse(null);
        } catch (CetpFault e) {
            log.log(Level.SEVERE, "Could not get card for egkHandle: " + egkHandle, e);
        }
        return null;
    }

    public String getSmcbHandle(UserRuntimeConfig userRuntimeConfig) throws CetpFault {
        List<Card> cards = konnektorClient.getCards(userRuntimeConfig, SMC_B);
        Optional<Card> cardOpt = cards.stream()
            .filter(c -> "Praxis SigmuntowskíTEST-ONLY".equals(c.getCardHolderName()))
            .findAny();
        return cardOpt.map(Card::getCardHandle).orElse(cards.getFirst().getCardHandle());
    }

    public String getTelematikId(UserRuntimeConfig userRuntimeConfig, String smcbHandle) throws CetpFault {
        Pair<X509Certificate, Boolean> x509Certificate = konnektorClient.getSmcbX509Certificate(userRuntimeConfig, smcbHandle);
        return extractTelematikIdFromCertificate(x509Certificate.getKey());
    }

    public InsuranceData getInsuranceDataOrReadVSD(
        String telematikId,
        String correlationId,
        String egkHandle,
        UserRuntimeConfig runtimeConfig
    ) throws Exception {
        String kvnr = getKvnr(runtimeConfig, egkHandle);
        String smcbHandle = getSmcbHandle(runtimeConfig);
        return getInsuranceDataOrReadVSD(telematikId, kvnr, correlationId, smcbHandle, runtimeConfig);
    }

    public InsuranceData getInsuranceDataOrReadVSD(
        String telematikId,
        String kvnr,
        String correlationId,
        String smcbHandle,
        UserRuntimeConfig runtimeConfig
    ) throws Exception {
        InsuranceData localInsuranceData = getLocalInsuranceData(telematikId, kvnr);
        if (localInsuranceData != null) {
            return localInsuranceData;
        }
        folderService.applyTelematikPath(telematikId);

        String egkHandle = getEgkHandle(runtimeConfig, kvnr);
        ReadVSDResponse readVSDResponse = vsdService.readVSD(correlationId, egkHandle, smcbHandle, runtimeConfig);
        String xInsurantId = extractInsurantId(readVSDResponse);
        if (kvnr == null || kvnr.equals(xInsurantId)) {
            // ReadVSDResponseEx must be sent synchronously to get valid local InsuranceData.
            readVSDResponseExEvent.fire(new ReadVSDResponseEx(telematikId, xInsurantId, readVSDResponse));
            return getLocalInsuranceData(telematikId, xInsurantId);
        }
        throw new IllegalStateException(String.format("Mismatch found: KVNR=%s, xInsurantId=%s", kvnr, xInsurantId));
    }

    public InsuranceData getLocalInsuranceData(String telematikId, String kvnr) {
        if (kvnr == null) {
            return null;
        }
        File localVSDFolder = folderService.getInsurantMedFolder(telematikId, kvnr, "local");
        return webdavSmcbManager.getFileSystemInsuranceData(localVSDFolder, kvnr);
    }

    private String extractInsurantId(ReadVSDResponse readVSDResponse) throws Exception {
        Document doc = createDocument(readVSDResponse.getPruefungsnachweis(), true);
        String e = doc.getElementsByTagName("E").item(0).getTextContent();

        if (e.equals("3")) {
            byte[] bytes = readVSDResponse.getPersoenlicheVersichertendaten();
            UCPersoenlicheVersichertendatenXML patient = createUCEntity(bytes, true);
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
