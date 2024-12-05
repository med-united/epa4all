package de.servicehealth.epa4all.unit;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.health.service.cetp.IKonnektorClient;
import de.servicehealth.epa4all.AbstractVsdTest;
import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.insurance.ReadVSDResponseEx;
import de.servicehealth.epa4all.server.smcb.WebdavSmcbManager;
import de.servicehealth.epa4all.server.vsd.VSDService;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Stream;

import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InsuranceDataServiceTest extends AbstractVsdTest {

    public static final File TEST_FOLDER = new File("test-data");

    @BeforeEach
    public void beforeEach() {
        TEST_FOLDER.mkdir();
    }

    @AfterEach
    public void afterEach() {
        File[] files = TEST_FOLDER.listFiles();
        if (files != null) {
            Stream.of(files).forEach(f -> {
                if (f.isDirectory()) {
                    try {
                        deleteDirectory(f);
                    } catch (IOException e) {
                        System.out.println(e.getMessage());
                    }
                } else {
                    f.delete();
                }
            });
        }
    }

    @Test
    public void firesReadVSDResponseWhichCreatesLocalInsuranceFiles() throws Exception {
        WebdavConfig webdavConfig = mock(WebdavConfig.class);
        when(webdavConfig.getRootFolder()).thenReturn(TEST_FOLDER.getAbsolutePath());
        when(webdavConfig.getSmcbFolders()).thenReturn(
            Set.of(
                "eab_2ed345b1-35a3-49e1-a4af-d71ca4f23e57",
                "other_605a9f3c-bfe8-4830-a3e3-25a4ec6612cb",
                "local_00000000-0000-0000-0000-000000000000"
            )
        );

        String telematikId = "telematikId";

        FolderService folderService = new FolderService(webdavConfig);
        folderService.applyTelematikPath(telematikId);

        String kvnr = "X110485291";

        String egkHandle = "EGK-123";
        String smcbHandle = "SMC-B-123";

        IKonnektorClient konnektorClient = mock(IKonnektorClient.class);
        when(konnektorClient.getEgkHandle(any(), any())).thenReturn(egkHandle);
        when(konnektorClient.getSmcbHandle(any())).thenReturn(smcbHandle);

        WebdavSmcbManager webdavSmcbManager = new WebdavSmcbManager(folderService, webdavConfig);

        VSDService vsdService = mock(VSDService.class);
        ReadVSDResponse readVSDResponse = prepareReadVSDResponse();
        when(vsdService.readVSD(any(), any(), any())).thenReturn(readVSDResponse);
        ReadVSDResponseEx readVSDResponseEx = new ReadVSDResponseEx(telematikId, kvnr, readVSDResponse);

        Event<ReadVSDResponseEx> readVSDResponseExEvent = mock(Event.class);
        doAnswer((Answer<Void>) invocation -> {
            webdavSmcbManager.onRead(readVSDResponseEx);
            return null;
        }).when(readVSDResponseExEvent).fire(eq(readVSDResponseEx));

        InsuranceDataService insuranceDataService = new InsuranceDataService(
            webdavSmcbManager,
            konnektorClient,
            folderService,
            vsdService,
            readVSDResponseExEvent
        );
        InsuranceData insuranceData = insuranceDataService.getInsuranceDataOrReadVSD(telematikId, kvnr, smcbHandle, null);

        assertEquals(kvnr, insuranceData.getInsurantId());
    }
}
