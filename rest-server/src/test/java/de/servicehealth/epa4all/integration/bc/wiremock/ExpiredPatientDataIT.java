package de.servicehealth.epa4all.integration.bc.wiremock;

import de.servicehealth.api.epa4all.jmx.EpaMXBeanManager;
import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.bc.wiremock.setup.CallInfo;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.insurance.PatientDataJob;
import de.servicehealth.epa4all.server.jmx.TelematikMXBean;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.path.xml.XmlPath;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static de.servicehealth.epa4all.common.TestUtils.getBinaryFixture;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("SameParameterValue")
@QuarkusTest
@TestProfile(WireMockProfile.class)
public class ExpiredPatientDataIT extends AbstractJCRTest {

    @Inject
    EpaFileDownloader fileDownloader;

    @Inject
    PatientDataJob patientDataJob;

    @Test
    public void pdfReceivedAndAllDataDeletedOnExpiration() throws Exception {
        String validToValue = LocalDateTime.ofInstant(Instant.now(), ZoneId.of("UTC")).format(ISO_LOCAL_DATE_TIME);
        String validToPayload = "{\"validTo\":\"" + validToValue + "\"}";

        prepareVauStubs(List.of(
            Pair.of("/epa/basic/api/v1/ps/entitlements", new CallInfo().withJsonPayload(validToPayload.getBytes(UTF_8))),
            Pair.of("/epa/medication/render/v1/eml/pdf", new CallInfo().withPdfPayload(getBinaryFixture("Medication-List.pdf")))
        ));
        prepareInformationStubs(204);

        String telematikId = "3-SMC-B-Testkarte--883110000147807";
        String kvnr = "X110485291";
        receiveCardInsertedEvent(fileDownloader, null, kvnr);
        assertFileSearch(telematikId, kvnr, "Ibuprofen", true);

        String objectName = TelematikMXBean.OBJECT_NAME.formatted(telematikId);
        TelematikMXBean telematikMXBean = EpaMXBeanManager.getMXBean(objectName, TelematikMXBean.class);
        assertNotNull(telematikMXBean);
        assertEquals(1, telematikMXBean.getPatientsCount());

        mockWebdavConfig(tempDir.toFile(), null, Duration.ofSeconds(1));
        patientDataJob.expirationMaintenance();

        assertFileSearch(telematikId, kvnr, "Ibuprofen", false);
        assertEquals(0, telematikMXBean.getPatientsCount());
    }

    private void assertFileSearch(String telematikId, String kvnr, String searchFor, boolean exists) throws Exception {
        TimeUnit.SECONDS.sleep(2);
        String resource = "/webdav2/" + telematikId + "/jcr:root/rootFolder/";
        String search = "SELECT * FROM [nt:resource] as r WHERE CONTAINS(r.*, '%s')".formatted(searchFor);
        XmlPath xmlPath = searchCall(resource, search, 207);

        // http://localhost:8889/webdav2/3-SMC-B-Testkarte--883110000147807/jcr%3aroot/rootFolder/X110485291/other/dc8ab4d6-4d20-4e0b-bb90-f78dd9d465b1.pdf/jcr%3acontent/
        List<String> hrefs = xmlPath.getList("multistatus.response.href").stream().map(String::valueOf).toList();
        if (exists) {
            assertEquals(1, hrefs.size());
            assertTrue(hrefs.getFirst().contains(kvnr + "/other/"));

            File otherFolder = new File(tempDir.toFile(), "webdav/" + telematikId + "/" + kvnr + "/other");
            File[] pdfFiles = otherFolder.listFiles(name -> name.getName().endsWith(".pdf"));
            assertNotNull(pdfFiles);
            assertEquals(1, pdfFiles.length);
            assertTrue(hrefs.getFirst().contains(pdfFiles[0].getName()));
        } else {
            File kvnrFolder = new File(tempDir.toFile(), "webdav/" + telematikId + "/" + kvnr);
            assertFalse(kvnrFolder.exists());
            assertEquals(0, hrefs.size());

            resource = "/webdav2/" + telematikId + "/jcr:root/rootFolder/" + kvnr;
            xmlPath = searchCall(resource, search, 409);
            List<String> exceptions = xmlPath.getList("exception").stream().map(String::valueOf).toList();
            assertEquals(1, exceptions.size());
            assertTrue(exceptions.getFirst().contains("No child node definition for " + kvnr + " found in node /rootFolder"));
        }
    }
}