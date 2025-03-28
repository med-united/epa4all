package de.servicehealth.epa4all.integration.bc.wiremock;

import de.servicehealth.epa4all.common.profile.WireMockProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.path.xml.XmlPath;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("UnusedReturnValue")
@QuarkusTest
@TestProfile(WireMockProfile.class)
public class WebdavJCRIT extends AbstractJCRTest {

    @Test
    public void fulltextSearchNtFilesAndResources() throws Exception {
        jcrService.doStart();

        String telematikId = "1-SMC-B-Testkarte--883110000162363";
        String kvnr = "X110485291";
        prepareInsurantFiles(telematikId, kvnr);

        String resource = "/webdav2/" + telematikId + "/jcr:root/rootFolder/" + kvnr + "/local";

        String propFind = """
            <propfind xmlns="DAV:" xmlns:epa="https://www.service-health.de/epa">
                <prop>
                    <epa:getlastmodified/>
                    <epa:displayname/>
                </prop>
            </propfind>
            """;

        XmlPath xmlPath = propfindCall(resource, propFind);

        List<String> hrefs = xmlPath.getList("multistatus.response.href").stream().map(String::valueOf).toList();
        assertEquals(13, hrefs.size());

        resource = "/webdav2/" + telematikId + "/jcr:root/rootFolder";

        String query = "SELECT * FROM [nt:resource] as r WHERE CONTAINS(r.*, '%s')".formatted("Johann Alfons Simon Heider");
        xmlPath = searchCall(resource, query, 207);

        hrefs = xmlPath.getList("multistatus.response.href").stream().map(String::valueOf).toList();
        assertEquals(1, hrefs.size());

        query = """
            SELECT f.[epa:firstname], f.[epa:lastname] FROM [nt:file] as f
             WHERE CONTAINS(f.[epa:firstname], '%s')
            """.formatted("Simon");
        xmlPath = searchCall(resource, query, 207);

        List<String> firstnames = xmlPath.get("**.findAll { it.name == 'f.epa:firstname'}.value");
        assertEquals(5, firstnames.size());
        assertEquals("Johann_x0020_Alfons_x0020_Simon_x0020_Heider", firstnames.getFirst());

        // TODO implement DASL query

        // query = """
        //     select * from [nt:file] as f
        //     order by f.[epa:displayname]
        //     option(offset 0, limit 2)
        //     """;
        //
        // xmlPath = searchCall(resource, query);
        //
        // hrefs = xmlPath.getList("multistatus.response.href").stream().map(String::valueOf).toList();
        // assertEquals(2, hrefs.size());
        // List<String> displaynames = xmlPath.get("**.findAll { it.name == 'f.epa:displayname'}.value");
        // assertEquals("AllgemeineVersicherungsdaten.xml", displaynames.getFirst());
        // assertEquals("GeschuetzteVersichertendaten.xml", displaynames.getLast());
    }
}