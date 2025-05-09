package de.servicehealth.epa4all.server.entitlement;

import de.gematik.ws.fa.vsdm.vsd.v5.UCAllgemeineVersicherungsdatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.api.epa4all.EpaAPI;
import de.servicehealth.api.epa4all.entitlement.EntitlementsApi;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.model.EntitlementRequestType;
import de.servicehealth.model.ValidToResponseType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

@ApplicationScoped
public class EntitlementService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementService.class.getName());

    IdpClient idpClient;
    VauNpProvider vauNpProvider;
    IKonnektorClient konnektorClient;
    InsuranceDataService insuranceDataService;

    @Inject
    public EntitlementService(
        IdpClient idpClient,
        VauNpProvider vauNpProvider,
        IKonnektorClient konnektorClient,
        InsuranceDataService insuranceDataService
    ) {
        this.idpClient = idpClient;
        this.vauNpProvider = vauNpProvider;
        this.konnektorClient = konnektorClient;
        this.insuranceDataService = insuranceDataService;
    }

    public Instant getEntitlementExpiry(
        UserRuntimeConfig runtimeConfig,
        InsuranceData insuranceData,
        EpaAPI epaApi,
        String userAgent,
        String smcbHandle,
        String telematikId,
        String insurantId
    ) {
        try {
            Instant validTo = insuranceDataService.getEntitlementExpiry(telematikId, insurantId);
            log.info("Current entitlement-expiry = {}", validTo);
            if (validTo != null && validTo.isAfter(Instant.now())) {
                return validTo;
            }
            if (insuranceData == null) {
                log.info("Call setEntitlement is skipped, insuranceData == NULL");
                return null;
            }
            return setEntitlement(
                runtimeConfig,
                insuranceData,
                epaApi,
                telematikId,
                userAgent,
                smcbHandle
            );
        } catch (Exception e) {
            log.error("Error while resolveEntitlement", e);
            return null;
        }
    }

    public Instant setEntitlement(
        UserRuntimeConfig userRuntimeConfig,
        InsuranceData insuranceData,
        EpaAPI epaAPI,
        String telematikId,
        String userAgent,
        String smcbHandle
    ) {
        String insurantId = insuranceData.getInsurantId();
        String pz = insuranceData.getPz();
        String hcv = extractHCV(insuranceData);
        String jwt = idpClient.createEntitlementPSJWT(smcbHandle, pz, hcv, userRuntimeConfig);

        EntitlementRequestType entitlementRequest = new EntitlementRequestType();
        entitlementRequest.setJwt(jwt);

        EntitlementsApi entitlementsApi = epaAPI.getEntitlementsApi();
        ValidToResponseType response = entitlementsApi.setEntitlementPs(
            insurantId, userAgent, epaAPI.getBackend(),
            "Apache-CXF/4.0.5", "Upgrade, HTTP2-Settings", "h2c", entitlementRequest
        );
        log.info("Updating local entitlement expiry with {}", response.getValidTo());
        Instant instant = response.getValidTo().toInstant();
        insuranceDataService.setEntitlementExpiry(instant, telematikId, insurantId);
        return instant;
    }

    public static synchronized String extractHCV(InsuranceData insuranceData) {
        try {
            UCAllgemeineVersicherungsdatenXML allgemeineVersicherungsdatenXML = insuranceData.getAllgemeineVersicherungsdaten();
            String vb = allgemeineVersicherungsdatenXML.getVersicherter().getVersicherungsschutz().getBeginn().replaceAll(" ", "");
            UCPersoenlicheVersichertendatenXML patient = insuranceData.getPersoenlicheVersichertendaten();
            UCPersoenlicheVersichertendatenXML.Versicherter.Person person = patient.getVersicherter().getPerson();
            UCPersoenlicheVersichertendatenXML.Versicherter.Person.StrassenAdresse strassenAdresse = person.getStrassenAdresse();
            String sas = strassenAdresse.getStrasse() == null ? "" : strassenAdresse.getStrasse().trim();
            log.info(String.format("extractHCV vb=%s, sas=%s", vb, sas));
            return calculateHCV(vb, sas);
        } catch (Exception e) {
            String msg = String.format("Could generate HCV message for KVNR=%s", insuranceData.getInsurantId());
            log.error(msg, e);
            return "";
        }
    }

    static String calculateHCV(String vb, String sas) throws Exception {
        byte[] vbb = vb.getBytes(ISO_8859_1);
        byte[] sasb = sas.getBytes(ISO_8859_1);
        byte[] combined = new byte[vbb.length + sasb.length];
        System.arraycopy(vbb, 0, combined, 0, vbb.length);
        System.arraycopy(sasb, 0, combined, vbb.length, sasb.length);
        byte[] sha256 = MessageDigest.getInstance("SHA-256").digest(combined);
        byte[] first5 = new byte[5];
        System.arraycopy(sha256, 0, first5, 0, 5);
        first5[0] = (byte) (first5[0] & 127);
        return Base64.getEncoder().encodeToString(first5);
    }
}
