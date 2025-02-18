package de.servicehealth.epa4all.integration.nonbc;

import de.health.service.cetp.IKonnektorClient;
import de.servicehealth.epa4all.common.profile.ProxyLocalTestProfile;
import de.servicehealth.epa4all.integration.base.AbstractVsdTest;
import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.vsd.VsdService;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(ProxyLocalTestProfile.class)
public class InsuranceDataIT extends AbstractVsdTest {

    @Test
    public void fireReadVsdResponseCreatesLocalInsuranceFiles() throws Exception {
        String telematikId = "telematikId";
        String kvnr = "X110683202";
        String egkHandle = "EGK-123";
        String smcbHandle = "SMC-B-123";

        mockWebdavConfig(TEST_FOLDER);
        mockKonnectorClient(egkHandle, telematikId, kvnr, smcbHandle);
        mockVsdService(kvnr);

        String insurantId = vsdService.readVsd(telematikId, egkHandle, null, smcbHandle, null);
        InsuranceData insuranceData = insuranceDataService.getData(telematikId, insurantId);
        assertEquals(kvnr, insuranceData.getInsurantId());
    }

    @AfterEach
    public void afterEachEx() {
        QuarkusMock.installMockForType(webdavConfig, WebdavConfig.class);
        QuarkusMock.installMockForType(konnektorClient, IKonnektorClient.class);
        QuarkusMock.installMockForType(vsdService, VsdService.class);
    }
}
