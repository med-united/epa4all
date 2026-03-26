package de.servicehealth.epa4all.integration.bc.wiremock;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.server.jcr.JcrService;
import de.servicehealth.epa4all.server.kim.KimConfig;
import de.servicehealth.epa4all.server.kim.SmtpConfig;
import de.servicehealth.epa4all.server.presription.requestdata.OrganizationData;
import de.servicehealth.epa4all.server.presription.requestdata.PatientData;
import de.servicehealth.epa4all.server.presription.requestdata.PractitionerData;
import de.servicehealth.epa4all.server.rest.EPrescription;
import de.servicehealth.epa4all.server.rest.EquipmentPrescription;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.icegreen.greenmail.util.ServerSetup.PROTOCOL_SMTP;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(WireMockProfile.class)
public class KimPrescriptionIT extends AbstractWiremockTest {

    @Inject
    KimConfig kimConfig;

    @Inject
    SmtpConfig smtpConfig;

    @Inject
    JcrService jcrService;

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

    @Test
    public void prescriptionSentToKim() throws Exception {
        jcrService.doStart();

        String telematikId = "1-SMC-B-Testkarte--883110000162363";
        String kvnr = "X110587452";
        prepareInsurantFiles(telematikId, kvnr);

        String epaMedicationBase64 = "eyJyZXNvdXJjZVR5cGUiOiJNZWRpY2F0aW9uIiwiaWQiOiJiOTA2MWRjZi1hZTAwLTQ3MmMtODJiNS1kOGYyOTEwNGIzNWMiLCJtZXRhIjp7InByb2ZpbGUiOlsiaHR0cHM6Ly9nZW1hdGlrLmRlL2ZoaXIvZXBhLW1lZGljYXRpb24vU3RydWN0dXJlRGVmaW5pdGlvbi9lcGEtbWVkaWNhdGlvbnwxLjAuMyJdLCJsYXN0VXBkYXRlZCI6IjIwMjUtMDItMTdUMDk6NDc6MjkuMDE2NzU3WiJ9LCJleHRlbnNpb24iOlt7InVybCI6Imh0dHBzOi8vZ2VtYXRpay5kZS9maGlyL2VwYS1tZWRpY2F0aW9uL1N0cnVjdHVyZURlZmluaXRpb24vZHJ1Zy1jYXRlZ29yeS1leHRlbnNpb24iLCJ2YWx1ZUNvZGluZyI6eyJzeXN0ZW0iOiJodHRwczovL2dlbWF0aWsuZGUvZmhpci9lcGEtbWVkaWNhdGlvbi9Db2RlU3lzdGVtL2VwYS1kcnVnLWNhdGVnb3J5LWNzIiwiY29kZSI6IjAwIn19LHsidXJsIjoiaHR0cHM6Ly9nZW1hdGlrLmRlL2ZoaXIvZXBhLW1lZGljYXRpb24vU3RydWN0dXJlRGVmaW5pdGlvbi9tZWRpY2F0aW9uLWlkLXZhY2NpbmUtZXh0ZW5zaW9uIiwidmFsdWVCb29sZWFuIjpmYWxzZX0seyJ1cmwiOiJodHRwczovL2dlbWF0aWsuZGUvZmhpci9lcGEtbWVkaWNhdGlvbi9TdHJ1Y3R1cmVEZWZpbml0aW9uL3J4LXByZXNjcmlwdGlvbi1wcm9jZXNzLWlkZW50aWZpZXItZXh0ZW5zaW9uIiwidmFsdWVJZGVudGlmaWVyIjp7InN5c3RlbSI6Imh0dHBzOi8vZ2VtYXRpay5kZS9maGlyL2VwYS1tZWRpY2F0aW9uL3NpZC9yeC1wcmVzY3JpcHRpb24tcHJvY2Vzcy1pZGVudGlmaWVyIiwidmFsdWUiOiIxNjAuMDAwLjEwNi44NzguMjQxLjk1XzIwMjUwMjE3In19XSwiY29kZSI6eyJjb2RpbmciOlt7InN5c3RlbSI6Imh0dHA6Ly9maGlyLmRlL0NvZGVTeXN0ZW0vaWZhL3B6biIsImNvZGUiOiIwMDAyNzk1MCJ9XSwidGV4dCI6IklidXByb2ZlbiBBdGlkIDYwMG1nIDEwIFNUIn0sImZvcm0iOnsiY29kaW5nIjpbeyJzeXN0ZW0iOiJodHRwczovL2ZoaXIua2J2LmRlL0NvZGVTeXN0ZW0vS0JWX0NTX1NGSElSX0tCVl9EQVJSRUlDSFVOR1NGT1JNIiwiY29kZSI6IkZUQSJ9XX0sImFtb3VudCI6eyJudW1lcmF0b3IiOnsiZXh0ZW5zaW9uIjpbeyJ1cmwiOiJodHRwczovL2dlbWF0aWsuZGUvZmhpci9lcGEtbWVkaWNhdGlvbi9TdHJ1Y3R1cmVEZWZpbml0aW9uL21lZGljYXRpb24tcGFja2FnaW5nLXNpemUtZXh0ZW5zaW9uIiwidmFsdWVTdHJpbmciOiIxIn1dLCJ1bml0IjoiUGFrZXQiLCJzeXN0ZW0iOiJodHRwOi8vdW5pdHNvZm1lYXN1cmUub3JnIiwiY29kZSI6IntQYWNrYWdlfSJ9LCJkZW5vbWluYXRvciI6eyJ2YWx1ZSI6MX19LCJpZGVudGlmaWVyIjpbeyJzeXN0ZW0iOiJodHRwczovL2dlbWF0aWsuZGUvZmhpci9lcGEtbWVkaWNhdGlvbi9zaWQvZXBhLW1lZGljYXRpb24tdW5pcXVlLWlkZW50aWZpZXIiLCJ2YWx1ZSI6IjkwQkU1OTA5NEQ2Rjk1Nzk2MkMwODZFQkE4NzE3MTM0QjA3MTg2QzY1RTIxQkZCMUZCRUMzRDNCREEwQzdEQ0YifSx7InN5c3RlbSI6Imh0dHBzOi8vZ2VtYXRpay5kZS9maGlyL2VwYS1tZWRpY2F0aW9uL3NpZC9yeC1vcmlnaW5hdG9yLXByb2Nlc3MtaWRlbnRpZmllciIsInZhbHVlIjoiZjRkMzZkZGUtNWIxYy00ZGQyLTgzZDUtMjRjNTNlYjM4ZGFhXzE2MC4wMDAuMTA2Ljg3OC4yNDEuOTUifV0sInN0YXR1cyI6ImluYWN0aXZlIn0=";

        given()
            .queryParams(Map.of(X_KONNEKTOR, "localhost"))
            .queryParams(Map.of(X_INSURANT_ID, kvnr))
            .body(new EPrescription.EPrescriptionDto(
                epaMedicationBase64,
                "1-0-1-0",
                new PatientData(kvnr, "family", "given", "1984-06-11", "street", "035", "Berlin", "12233445"),
                new OrganizationData(telematikId, "orgName", "234"),
                new PractitionerData("doctor", "aibolit66@afrika.com")
            ))
            .contentType(APPLICATION_JSON)
            .when()
            .post("/e-prescription-kim-sender")
            .then()
            .body(containsString("OK"))
            .statusCode(200);

        assertReceivedContent("Ibuprofen Atid 600mg 10 ST");
    }

    @Test
    public void equipmentSentToKim() throws Exception {
        jcrService.doStart();

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