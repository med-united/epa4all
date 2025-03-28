package de.servicehealth.epa4all.integration.bc.wiremock;

import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.server.jcr.JcrConfig;
import de.servicehealth.epa4all.server.jcr.JcrService;
import de.servicehealth.epa4all.server.jcr.mixin.Mixin;
import de.servicehealth.epa4all.server.jcr.mixin.MixinsProvider;
import de.servicehealth.epa4all.server.jcr.prop.MixinProp;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.path.xml.XmlPath;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.creationdate;
import static de.servicehealth.epa4all.server.jcr.prop.MixinProp.EPA_NAMESPACE_PREFIX;
import static io.smallrye.common.constraint.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestProfile(WireMockProfile.class)
public class MixinJCRIT extends AbstractJCRTest {

    @Inject
    JcrConfig jcrConfig;

    @Inject
    JcrService jcrService;

    @Inject
    MixinsProvider mixinsProvider;

    @Test
    public void mixinsChangeCoveredCorrect() throws Exception {
        RestAssured.config = RestAssured.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.socket.timeout", 600000));

        // 1. prepare telematik+kvnr folders
        String telematikId = "1-SMC-B-Testkarte--883110000162363";
        String kvnr = "X110485291";
        prepareInsurantFiles(telematikId, kvnr);

        // 2. start jcr repository and import them
        jcrService.doStart();

        String resource = "/webdav2/" + telematikId + "/jcr:root/rootFolder/" + kvnr + "/local";

        String query = """
            SELECT f.[epa:firstname], f.[epa:lastname] FROM [nt:file] as f
            WHERE CONTAINS(f.[epa:firstname], '%s')
            """.formatted("Simon");

        XmlPath xmlPath = searchCall(resource, query, 207);

        List<String> firstnames = xmlPath.get("**.findAll { it.name == 'f.epa:firstname'}.value");
        assertEquals(6, firstnames.size());
        assertEquals("Johann_x0020_Alfons_x0020_Simon_x0020_Heider", firstnames.getFirst());

        jcrService.shutdown();

        String epaSpecial = "epa:special";

        MixinsProvider mixinsProvider = mock(MixinsProvider.class);
        when(mixinsProvider.getCurrentMixins()).thenReturn(List.of(new Mixin() {
            @Override
            public String getPrefix() {
                return EPA_NAMESPACE_PREFIX;
            }

            @Override
            public String getName() {
                return epaSpecial;
            }

            @Override
            public List<MixinProp> getProperties() {
                return List.of(creationdate);
            }
        }));
        QuarkusMock.installMockForType(mixinsProvider, MixinsProvider.class);

        Map<String, List<String>> map = Map.of(
            "nt:folder", List.of(epaSpecial),
            "nt:file", List.of(epaSpecial)
        );
        mockWebdavConfig(tempDir.toFile(), map, null);

        jcrService.doStart();

        query = "SELECT f.[epa:creationdate], f.[epa:firstname] FROM [nt:file] as f";
        xmlPath = searchCall(resource, query, 207);

        List<String> creationDates = xmlPath.get("**.findAll { it.name == 'f.epa:creationdate'}.value");
        assertEquals(6, creationDates.size());
        assertNotNull(creationDates.getFirst());

        firstnames = xmlPath.get("**.findAll { it.name == 'f.epa:firstname'}.value");
        assertEquals(6, firstnames.size());
        assertTrue(firstnames.getFirst().isEmpty());
    }

    @AfterEach
    public void afterEachEx() {
        QuarkusMock.installMockForType(mixinsProvider, MixinsProvider.class);
        QuarkusMock.installMockForType(jcrConfig, JcrConfig.class);
    }
}
