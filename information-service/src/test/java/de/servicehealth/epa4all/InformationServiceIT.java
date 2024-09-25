package de.servicehealth.epa4all;

import de.servicehealth.api.AccountInformationApi;
import de.servicehealth.epa4all.common.DevTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.yasson.JsonBindingProvider;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static de.servicehealth.epa4all.common.Utils.isDockerServiceRunning;
import static de.servicehealth.epa4all.cxf.utils.CxfUtils.initClient;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@QuarkusTest
@TestProfile(DevTestProfile.class)
public class InformationServiceIT {

    private static final Logger log = LoggerFactory.getLogger(InformationServiceIT.class);

    @Inject
    @ConfigProperty(name = "information-service.url")
    String informationServiceUrl;

    @Test
    public void callInformationServiceWorks() throws Exception {
        if (isDockerServiceRunning("information-service")) {
            JsonBindingProvider provider = new JsonBindingProvider();
            List<JsonBindingProvider> providers = new ArrayList<>();
            providers.add(provider);

            AccountInformationApi api = JAXRSClientFactory.create(
                informationServiceUrl, AccountInformationApi.class, providers
            );

            initClient(WebClient.client(api), List.of());

            assertDoesNotThrow(() -> api.getRecordStatus("Z1234567890", "PSSIM123456789012345/1.2.4"));
        } else {
            log.warn("Docker container for information-service is not running, skipping a test");
        }
    }
}
