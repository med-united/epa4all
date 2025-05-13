package de.servicehealth.epa4all.integration.bc.wiremock;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.server.jcr.JcrService;
import de.servicehealth.epa4all.server.kim.KimConfig;
import de.servicehealth.epa4all.server.kim.SmtpConfig;
import de.servicehealth.epa4all.server.rest.Prescription;
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
    public void kimEmailHasBeenSent() throws Exception {
        jcrService.doStart();

        String telematikId = "1-SMC-B-Testkarte--883110000162363";
        String kvnr = "X110587452";
        prepareInsurantFiles(telematikId, kvnr);

        String equipment = "TRACHEOFIRST® PRO; Art.-Nr.: 67800";
        given()
            .queryParams(Map.of(X_KONNEKTOR, "localhost"))
            .queryParams(Map.of(X_INSURANT_ID, kvnr))
            .body(new Prescription.PrescriptionDto(
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

        greenMail.waitForIncomingEmail(1);
        Message[] messages = greenMail.getReceivedMessages();
        assertEquals(1, messages.length);
        Message message = messages[0];
        assertEquals(kimConfig.getSubject(), message.getSubject());
        if (message.getContent() instanceof Multipart multipart) {
            BodyPart part = multipart.getBodyPart(1);
            String body = (String) part.getContent();
            assertTrue(body.contains(equipment));
        }
    }
}