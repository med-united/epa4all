package de.servicehealth.epa4all.integration.bc.wiremock;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.Entry;
import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.server.filetracker.FileEventSender;
import de.servicehealth.epa4all.server.kim.KimLdapConfig;
import de.servicehealth.epa4all.server.kim.KimSmtpConfig;
import de.servicehealth.epa4all.server.kim.SmtpConfig;
import de.servicehealth.epa4all.server.rest.EquipmentPrescription;
import de.servicehealth.utils.SSLUtils;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.net.InetAddress;
import java.net.URL;
import java.util.Map;

import static com.github.tomakehurst.wiremock.common.ResourceUtil.getResource;
import static com.icegreen.greenmail.util.ServerSetup.PROTOCOL_SMTP;
import static de.servicehealth.utils.SSLUtils.KeyStoreType.JKS;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unused")
@QuarkusTest
@TestProfile(WireMockProfile.class)
public class KimPrescriptionIT extends AbstractWiremockTest {

    @InjectMock
    FileEventSender suppressJcrHandling;

    @Inject
    KimLdapConfig ldapConfig;

    @Inject
    KimSmtpConfig kimConfig;

    @Inject
    SmtpConfig smtpConfig;

    GreenMail greenMail;

    @BeforeEach
    void setUp() {
        greenMail = new GreenMail(new ServerSetup(smtpConfig.getPort(), null, PROTOCOL_SMTP));
        greenMail.start();
    }

    @AfterEach
    void tearDown() {
        greenMail.stop();
    }

    private InMemoryDirectoryServer startLdapServer(boolean positiveFlow, String practitionerName) throws Exception {
        URL resource = getResource(KimPrescriptionIT.class, "keystore");
        SSLContext sslContext = SSLUtils.createSSLContextBundle(resource.openStream(), "password", JKS).getSslContext();
        InMemoryListenerConfig listenerConfig = InMemoryListenerConfig.createLDAPSConfig(
            "test-ssl",
            InetAddress.getByName("localhost"),
            ldapConfig.getLdapPort(),
            sslContext.getServerSocketFactory(),
            sslContext.getSocketFactory(),
            true,
            true
        );
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=data,dc=vzd");
        config.setListenerConfigs(listenerConfig);
        config.setSchema(null);

        InMemoryDirectoryServer server = new InMemoryDirectoryServer(config);
        server.add(new Entry(
            "dn: dc=data,dc=vzd",
            "objectClass: top",
            "objectClass: domain",
            "dc: data"
        ));
        server.add(new Entry(
            "dn: cn=Tanja Müller,dc=data,dc=vzd",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "cn: Tanja Müller",
            "sn: Müller",
            "displayName: " + (positiveFlow ? practitionerName : "some other name"),
            "rfc822mailbox: dr.mueller@praxis.kim.telematik",
            "professionOID: 1.2.276.0.76.4.54"
        ));
        return server;
    }

    @Test
    public void prescriptionSentToKim() throws Exception {
        doPrescriptionFlow(true, 200, "OK");
        assertReceivedContent("100mg Amoxicillin N2");
    }

    @Test
    public void prescriptionWasNotSentToKim() throws Exception {
        doPrescriptionFlow(false, 404, "not found");
    }

    private void doPrescriptionFlow(boolean positiveFlow, int expectedStatus, String expectedBodyPart) throws Exception {
        String practitionerName = "Dr. med. Erika Musterarzt";
        try (InMemoryDirectoryServer ldapServer = startLdapServer(positiveFlow, practitionerName)) {
            ldapServer.startListening();
            String telematikId = "1-SMC-B-Testkarte--883110000162363";
            String kvnr = "X110587452";
            prepareInsurantFiles(telematikId, kvnr);

            String epaBundle = "{\"resourceType\":\"Bundle\",\"id\":\"epa-medication-bundle-example\",\"meta\":{\"lastUpdated\":\"2024-11-18T20:56:32.415+01:00\",\"profile\":[\"https://gematik.de/fhir/epa-medication/StructureDefinition/epa-op-provide-prescription-erp-input-parameters|1.0.3\"]},\"type\":\"collection\",\"timestamp\":\"2024-11-18T20:56:32.415+01:00\",\"entry\":[{\"fullUrl\":\"http://epa4all/epa/medication/api/v1/fhir/Patient/patient-X110485291\",\"resource\":{\"resourceType\":\"Patient\",\"id\":\"patient-X110485291\",\"meta\":{\"profile\":[\"http://fhir.de/StructureDefinition/patient-de-basis|1.5.0\"]},\"identifier\":[{\"type\":{\"coding\":[{\"system\":\"http://fhir.de/CodeSystem/identifier-type-de-basis\",\"code\":\"GKV\"}]},\"system\":\"http://fhir.de/sid/gkv/kvid-10\",\"value\":\"X110485291\"}],\"name\":[{\"use\":\"official\",\"family\":\"Mustermann\",\"given\":[\"Max\"]}],\"birthDate\":\"1985-03-15\",\"address\":[{\"type\":\"both\",\"line\":[\"Musterstraße 1\"],\"city\":\"Berlin\",\"postalCode\":\"10115\",\"country\":\"DE\"}]}},{\"fullUrl\":\"http://epa4all/epa/medication/api/v1/fhir/Practitioner/practitioner-a494939b\",\"resource\":{\"resourceType\":\"Practitioner\",\"id\":\"practitioner-a494939b\",\"meta\":{\"profile\":[\"https://gematik.de/fhir/directory/StructureDefinition/PractitionerDirectory|0.11.12\"]},\"identifier\":[{\"type\":{\"coding\":[{\"system\":\"http://terminology.hl7.org/CodeSystem/v2-0203\",\"code\":\"LANR\"}]},\"system\":\"https://fhir.kbv.de/NamingSystem/KBV_NS_Base_ANR\",\"value\":\"123456789\"}],\"name\":[{\"use\":\"official\",\"family\":\"Musterarzt\",\"given\":[\"Erika\"],\"prefix\":[\"Dr. med.\"]}],\"qualification\":[{\"code\":{\"coding\":[{\"system\":\"https://fhir.kbv.de/CodeSystem/KBV_CS_SFHIR_BAR2_WBO\",\"code\":\"010\",\"display\":\"FA Allgemeinmedizin\"}]}}]}},{\"fullUrl\":\"http://epa4all/epa/medication/api/v1/fhir/Medication/f1d16a71-9a98-4fba-9d58-51a522f334f4\",\"resource\":{\"resourceType\":\"Medication\",\"id\":\"f1d16a71-9a98-4fba-9d58-51a522f334f4\",\"meta\":{\"versionId\":\"0\",\"lastUpdated\":\"2024-11-18T20:56:32.415+01:00\",\"profile\":[\"https://gematik.de/fhir/epa-medication/StructureDefinition/epa-medication|1.0.3\"]},\"extension\":[{\"url\":\"https://gematik.de/fhir/epa-medication/StructureDefinition/drug-category-extension\",\"valueCoding\":{\"system\":\"https://gematik.de/fhir/epa-medication/CodeSystem/epa-drug-category-cs\",\"code\":\"00\"}},{\"url\":\"https://gematik.de/fhir/epa-medication/StructureDefinition/medication-id-vaccine-extension\",\"valueBoolean\":false},{\"url\":\"https://gematik.de/fhir/epa-medication/StructureDefinition/rx-prescription-process-identifier-extension\",\"valueIdentifier\":{\"system\":\"https://gematik.de/fhir/epa-medication/sid/rx-prescription-process-identifier\",\"value\":\"160.000.103.009.543.42_authoredOn[20240018]\"}}],\"identifier\":[{\"system\":\"https://gematik.de/fhir/epa-medication/sid/epa-medication-unique-identifier\",\"value\":\"A55ECBD78DEBCE53BB627857358DC25038863ACE531E2DD2189ECA2C9C48103F\"},{\"system\":\"https://gematik.de/fhir/epa-medication/sid/rx-originator-process-identifier\",\"value\":\"37992601-3715-4acd-be30-c00e7bc50e90_160.000.103.009.543.42\"}],\"code\":{\"text\":\"100mg Amoxicillin N2\"},\"status\":\"inactive\"}},{\"fullUrl\":\"http://epa4all/epa/medication/api/v1/fhir/MedicationRequest/01ba2c64-1e04-46e9-a462-ccec201f4486\",\"resource\":{\"resourceType\":\"MedicationRequest\",\"id\":\"01ba2c64-1e04-46e9-a462-ccec201f4486\",\"meta\":{\"versionId\":\"0\",\"lastUpdated\":\"2024-11-18T20:56:32.498+01:00\",\"profile\":[\"https://gematik.de/fhir/epa-medication/StructureDefinition/epa-medication-request|1.0.3\"]},\"extension\":[{\"url\":\"https://gematik.de/fhir/epa-medication/StructureDefinition/multiple-prescription-extension\",\"extension\":[{\"url\":\"indicator\",\"valueBoolean\":false}]}],\"identifier\":[{\"system\":\"https://gematik.de/fhir/epa-medication/sid/rx-prescription-process-identifier\",\"value\":\"160.000.103.009.543.42_authoredOn[20240018]\"},{\"system\":\"https://gematik.de/fhir/epa-medication/sid/rx-originator-process-identifier\",\"value\":\"b4ad2286-2979-446b-89d3-82c0c530c16b_160.000.103.009.543.42\"}],\"status\":\"active\",\"intent\":\"filler-order\",\"medicationReference\":{\"reference\":\"Medication/f1d16a71-9a98-4fba-9d58-51a522f334f4\"},\"subject\":{\"reference\":\"Patient/patient-X110485291\",\"identifier\":{\"system\":\"http://fhir.de/sid/gkv/kvid-10\",\"value\":\"X110485291\"}},\"authoredOn\":\"2024-11-18\",\"requester\":{\"reference\":\"Practitioner/practitioner-a494939b\"},\"dosageInstruction\":[{\"text\":\"1-1-1\"}],\"dispenseRequest\":{\"quantity\":{\"value\":1,\"system\":\"http://unitsofmeasure.org\",\"code\":\"{Package}\"}},\"substitution\":{\"allowedBoolean\":true}}}]}";

            given()
                .queryParams(Map.of(X_KONNEKTOR, "localhost"))
                .body(epaBundle)
                .contentType("application/fhir+json")
                .when()
                .post("/e-prescription-kim-sender")
                .then()
                .body(containsString(expectedBodyPart))
                .statusCode(expectedStatus);
        }
    }

    @Test
    public void equipmentSentToKim() throws Exception {
        String telematikId = "1-SMC-B-Testkarte--883110000162363";
        String kvnr = "X110587452";
        prepareInsurantFiles(telematikId, kvnr);

        String equipment = "TRACHEOFIRST® PRO; Art.-Nr.: 67800";
        given()
            .queryParams(Map.of(X_KONNEKTOR, "localhost"))
            .queryParams(Map.of(X_INSURANT_ID, kvnr))
            .body(new EquipmentPrescription.PrescriptionDto(
                equipment,
                "lanr",
                "prefix",
                "bsnr",
                "+491736322621",
                "Hallo Apotheke!"
            ))
            .contentType(APPLICATION_JSON)
            .when()
            .post("/prescription")
            .then()
            .body(containsString("OK"))
            .statusCode(200);

        assertReceivedContent(equipment);
    }

    private void assertReceivedContent(String expected) throws Exception {
        greenMail.waitForIncomingEmail(1);
        Message[] messages = greenMail.getReceivedMessages();
        assertEquals(1, messages.length);
        Message message = messages[0];
        assertEquals(kimConfig.getSubject(), message.getSubject());
        if (message.getContent() instanceof Multipart multipart) {
            BodyPart part = multipart.getBodyPart(1);
            String body = (String) part.getContent();
            assertTrue(body.contains(expected));
        }
    }
}