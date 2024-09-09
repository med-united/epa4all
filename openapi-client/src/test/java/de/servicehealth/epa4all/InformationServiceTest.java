package de.servicehealth.epa4all;

import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import de.servicehealth.api.AccountInformationApi;
import org.apache.cxf.jaxrs.client.Client;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class InformationServiceTest {

    private final static Logger log = LoggerFactory.getLogger(InformationServiceTest.class);

    @Test
    public void callInformationServiceWorks() throws Exception {
        if (informationServiceRunning()) {
            JacksonJsonProvider provider = new JacksonJsonProvider();
            List<JacksonJsonProvider> providers = new ArrayList<>();
            providers.add(provider);

            AccountInformationApi api = JAXRSClientFactory.create("http://localhost:8082", AccountInformationApi.class, providers);
            Client client = WebClient.client(api);
            ClientConfiguration config = WebClient.getConfig(client);
            assertNotNull(config);

            assertDoesNotThrow(() -> api.getRecordStatus("Z1234567890", "PSSIM123456789012345/1.2.4"));
        } else {
            log.warn("Docker container for information-service is not running, skipping a test");
        }
    }

    public boolean informationServiceRunning() throws Exception {
        String containerName = "information-service";
        ProcessBuilder processBuilder = new ProcessBuilder("docker", "ps", "--filter", "name=" + containerName, "--format", "{{.Names}}");
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        process.waitFor(5, TimeUnit.SECONDS);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String output = reader.lines().collect(Collectors.joining("\n"));
            return output.contains(containerName);
        }
    }
}
