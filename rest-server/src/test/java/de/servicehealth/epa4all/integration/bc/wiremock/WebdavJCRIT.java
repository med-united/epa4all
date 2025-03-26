package de.servicehealth.epa4all.integration.bc.wiremock;

import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.server.jcr.JcrService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.path.xml.XmlPath;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("UnusedReturnValue")
@QuarkusTest
@TestProfile(WireMockProfile.class)
public class WebdavJCRIT extends AbstractJCRTest {

    @Inject
    JcrService jcrService;

    @Test
    public void fulltextSearchNtFilesAndResources() throws Exception {
        String telematikId = "1-SMC-B-Testkarte--883110000162363";
        String kvnr = "X110485291";
        prepareInsurantFiles(telematikId, kvnr);

        jcrService.doStart();

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

        String search = """
            <d:searchrequest xmlns:d="DAV:" >
                <d:JCR-SQL2><![CDATA[
                    SELECT * FROM [nt:resource] as r WHERE CONTAINS(r.*, '%s')
                ]]></d:JCR-SQL2>
            </d:searchrequest>
            """.formatted("Johann Alfons Simon Heider");

        xmlPath = searchCall(resource, search);

        hrefs = xmlPath.getList("multistatus.response.href").stream().map(String::valueOf).toList();
        assertEquals(1, hrefs.size());

        search = """
            <d:searchrequest xmlns:d="DAV:" >
                <d:JCR-SQL2><![CDATA[
                    SELECT f.[epa:firstname], f.[epa:lastname] FROM [nt:file] as f
                    WHERE CONTAINS(f.[epa:firstname], '%s')
                ]]></d:JCR-SQL2>
            </d:searchrequest>
            """.formatted("Simon");

        xmlPath = searchCall(resource, search);

        List<String> firstnames = xmlPath.get("**.findAll { it.name == 'f.epa:firstname'}.value");
        assertEquals(6, firstnames.size());
        assertEquals("Johann_x0020_Alfons_x0020_Simon_x0020_Heider", firstnames.getFirst());

        // TODO implement DASL search
        
        // search = """
        //     <d:searchrequest xmlns:d="DAV:" >
        //         <d:JCR-SQL2><![CDATA[
        //             select * from [nt:file] as f
        //             order by f.[epa:displayname]
        //             option(offset 0, limit 2)
        //         ]]></d:JCR-SQL2>
        //     </d:searchrequest>
        //     """;
        //
        // xmlPath = searchCall(resource, search);
        //
        // hrefs = xmlPath.getList("multistatus.response.href").stream().map(String::valueOf).toList();
        // assertEquals(2, hrefs.size());
        // List<String> displaynames = xmlPath.get("**.findAll { it.name == 'f.epa:displayname'}.value");
        // assertEquals("AllgemeineVersicherungsdaten.xml", displaynames.getFirst());
        // assertEquals("GeschuetzteVersichertendaten.xml", displaynames.getLast());
    }
}