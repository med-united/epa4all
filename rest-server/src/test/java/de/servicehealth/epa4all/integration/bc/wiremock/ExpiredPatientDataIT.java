package de.servicehealth.epa4all.integration.bc.wiremock;

import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.bc.wiremock.setup.CallInfo;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.path.xml.XmlPath;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static de.servicehealth.epa4all.common.TestUtils.getBinaryFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("SameParameterValue")
@QuarkusTest
@TestProfile(WireMockProfile.class)
public class ExpiredPatientDataIT extends AbstractJCRTest {

    @Inject
    EpaFileDownloader fileDownloader;

    @Test
    public void pdfReceivedAndAllDataDeletedOnExpiration() throws Exception {
        String ctId = "cardTerminal-124";

        prepareVauStubs(List.of(
            Pair.of(
                "/epa/medication/render/v1/eml/pdf",
                new CallInfo().withPdfPayload(getBinaryFixture("Medication-List.pdf"))
            )
        ));
        prepareInformationStubs(204);

        vauNpProvider.doStart();
        jcrService.doStart();

        String kvnr = "X110485291";
        receiveCardInsertedEvent(fileDownloader, kvnr, ctId);

        TimeUnit.SECONDS.sleep(3);

        assertFileSearch(kvnr, "Ibuprofen");
    }

    private void assertFileSearch(String kvnr, String searchFor) {
        String telematikId = "3-SMC-B-Testkarte--883110000147807";
        String resource = "/webdav2/" + telematikId + "/jcr:root/rootFolder/" + kvnr;
        String search = "SELECT * FROM [nt:resource] as r WHERE CONTAINS(r.*, '%s')".formatted(searchFor);
        XmlPath xmlPath = searchCall(resource, search);

        // http://localhost:8889/webdav2/3-SMC-B-Testkarte--883110000147807/jcr%3aroot/rootFolder/X110485291/other/dc8ab4d6-4d20-4e0b-bb90-f78dd9d465b1.pdf/jcr%3acontent/
        List<String> hrefs = xmlPath.getList("multistatus.response.href").stream().map(String::valueOf).toList();
        assertEquals(1, hrefs.size());
        assertTrue(hrefs.getFirst().contains(kvnr + "/other/"));

        File otherFolder = new File(tempDir.toFile(), "webdav/" + telematikId + "/" + kvnr + "/other");
        File[] pdfFiles = otherFolder.listFiles(name -> name.getName().endsWith(".pdf"));
        assertNotNull(pdfFiles);
        assertEquals(1, pdfFiles.length);
        assertTrue(hrefs.getFirst().contains(pdfFiles[0].getName()));
    }
}