package de.servicehealth.epa4all.integration.bc.xds;

import de.servicehealth.epa4all.common.profile.ProxyEpaTestProfile;
import de.servicehealth.epa4all.integration.base.AbstractVsdTest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import io.restassured.response.Response;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.common.TestUtils.deleteFiles;
import static de.servicehealth.epa4all.common.TestUtils.runWithEpaBackends;
import static de.servicehealth.epa4all.server.rest.xds.XdsResource.XDS_DOCUMENT_PATH;
import static de.servicehealth.vau.VauClient.KVNR;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(ProxyEpaTestProfile.class)
public class DownloadAllEpaIT extends AbstractVsdTest {

    private static final Logger log = Logger.getLogger(DownloadAllEpaIT.class.getName());

    private final String kvnr = "X110485291";

    @Override
    public void afterEach() {
        super.afterEach();
    }

    @Test
    public void allDocumentsWereDownloaded() throws Exception {
        RestAssured.defaultParser = Parser.XML;

        deleteFiles(TEST_FOLDER.listFiles());
        File[] files = TEST_FOLDER.listFiles();
        assertNotNull(files);
        assertEquals(0, files.length);
        Set<String> epaBackends = epaConfig.getEpaBackends();
        runWithEpaBackends(epaBackends, () -> {
            webdavConfig.setRootFolder(TEST_FOLDER.getAbsolutePath());

            Response adhoc = given().queryParam(KVNR, kvnr).when().get(XDS_DOCUMENT_PATH + "/query");
            AdhocQueryResponse adhocQueryResponse = adhoc.getBody().as(AdhocQueryResponse.class);
            int tasksSize = adhocQueryResponse.getRegistryObjectList().getIdentifiable().size();

            Response response = given().queryParam(KVNR, kvnr).when().get(XDS_DOCUMENT_PATH + "/downloadAll");
            assertEquals(200, response.getStatusCode());
            List<String> taskIds = Arrays.asList(response.getBody().print().split("\n"));
            assertEquals(tasksSize, taskIds.size());

            boolean done = taskIds.parallelStream().noneMatch(t -> {
                Response r = when().get(XDS_DOCUMENT_PATH + "/task/" + t);
                return r.body().print().contains("InProgress");
            });
            while (!done) {
                done = taskIds.parallelStream().noneMatch(t -> {
                    Response r = when().get(XDS_DOCUMENT_PATH + "/task/" + t);
                    return r.body().print().contains("InProgress");
                });
                TimeUnit.SECONDS.sleep(1);
                log.info("Waiting for download completion");
            }

            TimeUnit.SECONDS.sleep(5);

            File[] telematikFolders = folderService.getTelematikFolders();
            assertNotNull(telematikFolders);
            List<File> epaFiles = Arrays.stream(telematikFolders).flatMap(f -> {
                File[] kvnrFolders = folderService.getNestedFolders(f);
                assertNotNull(kvnrFolders);
                return Arrays.stream(kvnrFolders).flatMap(nf -> {
                    File[] dataFolders = folderService.getNestedFolders(nf);
                    assertNotNull(dataFolders);
                    return Arrays.stream(dataFolders)
                        .filter(df -> !df.getName().equals("local"))
                        .flatMap(df -> {
                            File[] dataFiles = folderService.getNestedFiles(df);
                            assertNotNull(dataFiles);
                            return Arrays.stream(dataFiles);
                        });
                });
            }).toList();
            assertEquals(tasksSize, epaFiles.size());
        });
    }
}
