package de.servicehealth.epa4all.integration.bc.epa;

import de.health.service.cetp.IKonnektorClient;
import de.servicehealth.epa4all.common.profile.ProxyEpaTestProfile;
import de.servicehealth.epa4all.integration.base.AbstractVsdTest;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.vsd.VsdResponseFile;
import de.servicehealth.epa4all.server.vsd.VsdService;
import de.servicehealth.folder.WebdavConfig;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static de.servicehealth.folder.IFolderService.LOCAL_FOLDER;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(ProxyEpaTestProfile.class)
public class InsuranceDataEpaIT extends AbstractVsdTest {

    private final String kvnr = "X110624006";

    private String egkHandle;
    private String smcbHandle;
    private String telematikId;

    @BeforeEach
    public void before() throws Exception {
        egkHandle = konnektorClient.getEgkHandle(defaultUserConfig, kvnr);
        smcbHandle = konnektorClient.getSmcbHandle(defaultUserConfig);
        telematikId = konnektorClient.getTelematikId(defaultUserConfig, smcbHandle);

        File localFolder = folderService.getMedFolder(telematikId, kvnr, LOCAL_FOLDER);
        new VsdResponseFile(localFolder).cleanUp();
    }

    @Test
    public void fireReadVsdResponseCreatesLocalInsuranceFiles() throws Exception {
        mockWebdavConfig(TEST_FOLDER, null);

        RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, defaultUserConfig.getUserConfigurations());
        String insurantId = vsdService.read(telematikId, egkHandle, runtimeConfig, smcbHandle, null);
        InsuranceData insuranceData = insuranceDataService.getData(telematikId, insurantId);
        assertEquals(kvnr, insuranceData.getInsurantId());
    }

    @AfterEach
    public void afterEachEx() {
        QuarkusMock.installMockForType(webdavConfig, WebdavConfig.class);
        QuarkusMock.installMockForType(konnektorClient, IKonnektorClient.class);
        QuarkusMock.installMockForType(vsdService, VsdService.class);
        QuarkusMock.installMockForType(folderService, FolderService.class);
    }
}
