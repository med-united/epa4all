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
import de.servicehealth.epa4all.server.presription.requestdata.EPrescriptionRequest;
import de.servicehealth.epa4all.server.presription.requestdata.OrganizationData;
import de.servicehealth.epa4all.server.presription.requestdata.PatientData;
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

    @Inject
    KimLdapConfig ldapConfig;

    @Inject
    KimSmtpConfig kimConfig;

    @Inject
    SmtpConfig smtpConfig;

    @InjectMock
    FileEventSender suppressJcrHandling;

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

    private InMemoryDirectoryServer startLdapServer(String practitionerName) throws Exception {
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
            "displayName: " + practitionerName,
            "rfc822mailbox: dr.mueller@praxis.kim.telematik",
            "professionOID: 1.2.276.0.76.4.54"
        ));
        return server;
    }

    @Test
    public void prescriptionSentToKim() throws Exception {
        startPrescriptionFlow(false, 200, "OK");
        assertReceivedContent("Ibuprofen Atid 600mg 10 ST");
    }

    @Test
    public void prescriptionWasNotSentToKim() throws Exception {
        startPrescriptionFlow(true, 404, "not found");
    }

    private void startPrescriptionFlow(boolean errorFlow, int expectedStatus, String expectedBodyPart) throws Exception {
        String practitionerName = "Praxis Dr. Müller";
        try (InMemoryDirectoryServer ldapServer = startLdapServer(practitionerName)) {
            ldapServer.startListening();
            String telematikId = "1-SMC-B-Testkarte--883110000162363";
            String kvnr = "X110587452";
            prepareInsurantFiles(telematikId, kvnr);

            String epaMedicationRequestBase64 = "eyJyZXNvdXJjZVR5cGUiOiJNZWRpY2F0aW9uUmVxdWVzdCIsImlkIjoiNWExMTM2MTQtZWU1Zi00ZDU2LWJiOGYtNzk3OTliMGZmOTgyIiwibWV0YSI6eyJwcm9maWxlIjpbImh0dHBzOi8vZ2VtYXRpay5kZS9maGlyL2VwYS1tZWRpY2F0aW9uL1N0cnVjdHVyZURlZmluaXRpb24vZXBhLW1lZGljYXRpb24tcmVxdWVzdHwxLjAuMyJdLCJsYXN0VXBkYXRlZCI6IjIwMjUtMDItMTdUMDk6NDc6MjkuMDM1MzA1WiJ9LCJleHRlbnNpb24iOlt7InVybCI6Imh0dHBzOi8vZ2VtYXRpay5kZS9maGlyL2VwYS1tZWRpY2F0aW9uL1N0cnVjdHVyZURlZmluaXRpb24vbXVsdGlwbGUtcHJlc2NyaXB0aW9uLWV4dGVuc2lvbiIsImV4dGVuc2lvbiI6W3sidXJsIjoiaW5kaWNhdG9yIiwidmFsdWVCb29sZWFuIjpmYWxzZX1dfV0sInN0YXR1cyI6ImFjdGl2ZSIsImludGVudCI6ImZpbGxlci1vcmRlciIsIm1lZGljYXRpb25SZWZlcmVuY2UiOnsicmVmZXJlbmNlIjoiTWVkaWNhdGlvbi9iOTA2MWRjZi1hZTAwLTQ3MmMtODJiNS1kOGYyOTEwNGIzNWMifSwic3ViamVjdCI6eyJpZGVudGlmaWVyIjp7InN5c3RlbSI6Imh0dHA6Ly9maGlyLmRlL3NpZC9na3Yva3ZpZC0xMCIsInZhbHVlIjoiWDExMDYyNDAwNiJ9fSwiYXV0aG9yZWRPbiI6IjIwMjUtMDItMTciLCJkb3NhZ2VJbnN0cnVjdGlvbiI6W3sidGV4dCI6IjAtMS0wIn1dLCJkaXNwZW5zZVJlcXVlc3QiOnsicXVhbnRpdHkiOnsidmFsdWUiOjEsInN5c3RlbSI6Imh0dHA6Ly91bml0c29mbWVhc3VyZS5vcmciLCJjb2RlIjoie1BhY2thZ2V9In19LCJzdWJzdGl0dXRpb24iOnsiYWxsb3dlZEJvb2xlYW4iOnRydWV9LCJpZGVudGlmaWVyIjpbeyJzeXN0ZW0iOiJodHRwczovL2dlbWF0aWsuZGUvZmhpci9lcGEtbWVkaWNhdGlvbi9zaWQvcngtcHJlc2NyaXB0aW9uLXByb2Nlc3MtaWRlbnRpZmllciIsInZhbHVlIjoiMTYwLjAwMC4xMDYuODc4LjI0MS45NV8yMDI1MDIxNyJ9LHsic3lzdGVtIjoiaHR0cHM6Ly9nZW1hdGlrLmRlL2ZoaXIvZXBhLW1lZGljYXRpb24vc2lkL3J4LW9yaWdpbmF0b3ItcHJvY2Vzcy1pZGVudGlmaWVyIiwidmFsdWUiOiI0NGZlNjcxNy1kMDc5LTQ4YzQtYmE5Yi1lZWVmNzQzODJlNWFfMTYwLjAwMC4xMDYuODc4LjI0MS45NSJ9XSwicmVxdWVzdGVyIjp7InJlZmVyZW5jZSI6IlByYWN0aXRpb25lclJvbGUvYjEzY2QxMmEtY2YwMC00ZTdkLWFiZmItYjQ3YzY1NTYxNmRkIn19";
            String epaMedicationBase64 = "eyJyZXNvdXJjZVR5cGUiOiJNZWRpY2F0aW9uIiwiaWQiOiJiOTA2MWRjZi1hZTAwLTQ3MmMtODJiNS1kOGYyOTEwNGIzNWMiLCJtZXRhIjp7InByb2ZpbGUiOlsiaHR0cHM6Ly9nZW1hdGlrLmRlL2ZoaXIvZXBhLW1lZGljYXRpb24vU3RydWN0dXJlRGVmaW5pdGlvbi9lcGEtbWVkaWNhdGlvbnwxLjAuMyJdLCJsYXN0VXBkYXRlZCI6IjIwMjUtMDItMTdUMDk6NDc6MjkuMDE2NzU3WiJ9LCJleHRlbnNpb24iOlt7InVybCI6Imh0dHBzOi8vZ2VtYXRpay5kZS9maGlyL2VwYS1tZWRpY2F0aW9uL1N0cnVjdHVyZURlZmluaXRpb24vZHJ1Zy1jYXRlZ29yeS1leHRlbnNpb24iLCJ2YWx1ZUNvZGluZyI6eyJzeXN0ZW0iOiJodHRwczovL2dlbWF0aWsuZGUvZmhpci9lcGEtbWVkaWNhdGlvbi9Db2RlU3lzdGVtL2VwYS1kcnVnLWNhdGVnb3J5LWNzIiwiY29kZSI6IjAwIn19LHsidXJsIjoiaHR0cHM6Ly9nZW1hdGlrLmRlL2ZoaXIvZXBhLW1lZGljYXRpb24vU3RydWN0dXJlRGVmaW5pdGlvbi9tZWRpY2F0aW9uLWlkLXZhY2NpbmUtZXh0ZW5zaW9uIiwidmFsdWVCb29sZWFuIjpmYWxzZX0seyJ1cmwiOiJodHRwczovL2dlbWF0aWsuZGUvZmhpci9lcGEtbWVkaWNhdGlvbi9TdHJ1Y3R1cmVEZWZpbml0aW9uL3J4LXByZXNjcmlwdGlvbi1wcm9jZXNzLWlkZW50aWZpZXItZXh0ZW5zaW9uIiwidmFsdWVJZGVudGlmaWVyIjp7InN5c3RlbSI6Imh0dHBzOi8vZ2VtYXRpay5kZS9maGlyL2VwYS1tZWRpY2F0aW9uL3NpZC9yeC1wcmVzY3JpcHRpb24tcHJvY2Vzcy1pZGVudGlmaWVyIiwidmFsdWUiOiIxNjAuMDAwLjEwNi44NzguMjQxLjk1XzIwMjUwMjE3In19XSwiY29kZSI6eyJjb2RpbmciOlt7InN5c3RlbSI6Imh0dHA6Ly9maGlyLmRlL0NvZGVTeXN0ZW0vaWZhL3B6biIsImNvZGUiOiIwMDAyNzk1MCJ9XSwidGV4dCI6IklidXByb2ZlbiBBdGlkIDYwMG1nIDEwIFNUIn0sImZvcm0iOnsiY29kaW5nIjpbeyJzeXN0ZW0iOiJodHRwczovL2ZoaXIua2J2LmRlL0NvZGVTeXN0ZW0vS0JWX0NTX1NGSElSX0tCVl9EQVJSRUlDSFVOR1NGT1JNIiwiY29kZSI6IkZUQSJ9XX0sImFtb3VudCI6eyJudW1lcmF0b3IiOnsiZXh0ZW5zaW9uIjpbeyJ1cmwiOiJodHRwczovL2dlbWF0aWsuZGUvZmhpci9lcGEtbWVkaWNhdGlvbi9TdHJ1Y3R1cmVEZWZpbml0aW9uL21lZGljYXRpb24tcGFja2FnaW5nLXNpemUtZXh0ZW5zaW9uIiwidmFsdWVTdHJpbmciOiIxIn1dLCJ1bml0IjoiUGFrZXQiLCJzeXN0ZW0iOiJodHRwOi8vdW5pdHNvZm1lYXN1cmUub3JnIiwiY29kZSI6IntQYWNrYWdlfSJ9LCJkZW5vbWluYXRvciI6eyJ2YWx1ZSI6MX19LCJpZGVudGlmaWVyIjpbeyJzeXN0ZW0iOiJodHRwczovL2dlbWF0aWsuZGUvZmhpci9lcGEtbWVkaWNhdGlvbi9zaWQvZXBhLW1lZGljYXRpb24tdW5pcXVlLWlkZW50aWZpZXIiLCJ2YWx1ZSI6IjkwQkU1OTA5NEQ2Rjk1Nzk2MkMwODZFQkE4NzE3MTM0QjA3MTg2QzY1RTIxQkZCMUZCRUMzRDNCREEwQzdEQ0YifSx7InN5c3RlbSI6Imh0dHBzOi8vZ2VtYXRpay5kZS9maGlyL2VwYS1tZWRpY2F0aW9uL3NpZC9yeC1vcmlnaW5hdG9yLXByb2Nlc3MtaWRlbnRpZmllciIsInZhbHVlIjoiZjRkMzZkZGUtNWIxYy00ZGQyLTgzZDUtMjRjNTNlYjM4ZGFhXzE2MC4wMDAuMTA2Ljg3OC4yNDEuOTUifV0sInN0YXR1cyI6ImluYWN0aXZlIn0=";

            given()
                .queryParams(Map.of(X_KONNEKTOR, "localhost"))
                .queryParams(Map.of(X_INSURANT_ID, kvnr))
                .body(new EPrescriptionRequest(
                    epaMedicationRequestBase64,
                    epaMedicationBase64,
                    errorFlow ? "wrongSearch" : practitionerName,
                    new PatientData(kvnr, "family", "given", "1984-06-11", "street", "035", "Berlin", "12233445"),
                    new OrganizationData(telematikId, "orgName", "234")
                ))
                .contentType(APPLICATION_JSON)
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