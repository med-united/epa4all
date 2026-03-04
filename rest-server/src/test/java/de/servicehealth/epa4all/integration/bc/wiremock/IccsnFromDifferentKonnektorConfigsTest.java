package de.servicehealth.epa4all.integration.bc.wiremock;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.cetp.konnektorconfig.KonnektorsConfigs;
import de.health.service.cetp.konnektorconfig.UserConfigurations;
import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.vsd.VsdConfig;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestProfile(WireMockProfile.class)
public class IccsnFromDifferentKonnektorConfigsTest extends AbstractWiremockTest {

    @Inject
    KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    IKonnektorClient konnektorClient;

    @Inject
    protected VsdConfig vsdConfig;

    @Test
    public void differentSmcbAreResolved() throws Exception {
        VsdConfig configMock = mock(VsdConfig.class);
        when(configMock.getPrimaryIccsn()).thenReturn("80276883110000141773");
        when(configMock.getTestSmcbCardholderName()).thenReturn("test-name");
        when(configMock.isHandlesTestMode()).thenReturn(false);
        QuarkusMock.installMockForType(configMock, VsdConfig.class);

        KonnektorsConfigs konnektorsConfigs = mock(KonnektorsConfigs.class);
        List<KonnektorConfig> configs = new ArrayList<>();
        KonnektorConfig config1 = prepareKonnektorConfig("80276883110000147807");
        KonnektorConfig config2 = prepareKonnektorConfig("80276883110000147805");
        configs.add(config1);
        configs.add(config2);

        when(konnektorsConfigs.getConfigs()).thenReturn(configs);

        RuntimeConfig runtimeConfig1 = new RuntimeConfig(konnektorDefaultConfig, config1.getUserConfigurations());
        String smcbHandle11 = konnektorClient.getSmcbHandle(runtimeConfig1);
        assertEquals("SMC-B-11", smcbHandle11);

        RuntimeConfig runtimeConfig2 = new RuntimeConfig(konnektorDefaultConfig, config2.getUserConfigurations());
        String smcbHandle3 = konnektorClient.getSmcbHandle(runtimeConfig2);
        assertEquals("SMC-B-3", smcbHandle3);

        QuarkusMock.installMockForType(vsdConfig, VsdConfig.class);
    }

    private KonnektorConfig prepareKonnektorConfig(String iccsn) {
        KonnektorConfig config = new KonnektorConfig();
        UserConfigurations userConfigurations = new UserConfigurations();
        userConfigurations.setIccsn(iccsn);
        userConfigurations.setConnectorBaseURL(konnektorDefaultConfig.getUrl());
        config.setUserConfigurations(userConfigurations);
        
        return config;
    }
}
