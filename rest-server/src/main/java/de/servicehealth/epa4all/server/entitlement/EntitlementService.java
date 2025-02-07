package de.servicehealth.epa4all.server.entitlement;

import de.gematik.ws.fa.vsdm.vsd.v5.UCAllgemeineVersicherungsdatenXML;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.health.service.config.api.UserRuntimeConfig;
import de.service.health.api.epa4all.EpaAPI;
import de.service.health.api.epa4all.entitlement.EntitlementsApi;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.model.EntitlementRequestType;
import de.servicehealth.model.ValidToResponseType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.HexFormat;

import static de.servicehealth.epa4all.server.vsd.VsdResponseFile.UNDEFINED_PZ;
import static java.nio.charset.StandardCharsets.ISO_8859_1;

@ApplicationScoped
public class EntitlementService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementService.class.getName());

    public static final String AUDIT_EVIDENCE_NO_DEFINED = "AuditEvidence is not defined";

    IdpClient idpClient;
    InsuranceDataService insuranceDataService;

    @Inject
    public EntitlementService(IdpClient idpClient, InsuranceDataService insuranceDataService) {
        this.idpClient = idpClient;
        this.insuranceDataService = insuranceDataService;
    }

    public boolean setEntitlement(
        UserRuntimeConfig userRuntimeConfig,
        InsuranceData insuranceData,
        EpaAPI epaAPI,
        String telematikId,
        String vauNp,
        String userAgent,
        String smcbHandle
    ) throws Exception {
        String insurantId = insuranceData.getInsurantId();
        String pz = insuranceData.getPz();
        if (UNDEFINED_PZ.equalsIgnoreCase(pz)) {
            String msg = String.format("%s for KVNR=%s, skipping the request", AUDIT_EVIDENCE_NO_DEFINED, insurantId);
            throw new AuditEvidenceException(msg);
        }
        String hcv = extractHCV(insuranceData);
        String jwt = idpClient.createEntitlementPSJWT(smcbHandle, pz, hcv, userRuntimeConfig);

        EntitlementRequestType entitlementRequest = new EntitlementRequestType();
        entitlementRequest.setJwt(jwt);

        EntitlementsApi entitlementsApi = epaAPI.getEntitlementsApi();
        ValidToResponseType response = entitlementsApi.setEntitlementPs(
            insurantId, userAgent, epaAPI.getBackend(), vauNp, entitlementRequest
        );
        if (response.getValidTo() != null) {
            insuranceDataService.updateEntitlement(response.getValidTo().toInstant(), telematikId, insurantId);
            return true;
        } else {
            return false;
        }
    }

    static synchronized String extractHCV(InsuranceData insuranceData) {
        try {
            UCAllgemeineVersicherungsdatenXML allgemeineVersicherungsdatenXML = insuranceData.getAllgemeineVersicherungsdaten();
            String vb = allgemeineVersicherungsdatenXML.getVersicherter().getVersicherungsschutz().getBeginn().replaceAll(" ", "");
            UCPersoenlicheVersichertendatenXML patient = insuranceData.getPersoenlicheVersichertendaten();
            UCPersoenlicheVersichertendatenXML.Versicherter.Person person = patient.getVersicherter().getPerson();
            UCPersoenlicheVersichertendatenXML.Versicherter.Person.StrassenAdresse strassenAdresse = person.getStrassenAdresse();
            String sas = strassenAdresse.getStrasse() == null ? "" : strassenAdresse.getStrasse().trim();
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
        return HexFormat.of().formatHex(first5);
    }
}
