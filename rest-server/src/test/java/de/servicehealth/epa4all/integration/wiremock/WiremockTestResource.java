package de.servicehealth.epa4all.integration.wiremock;

import de.servicehealth.epa4all.server.idp.DiscoveryDocumentFile;
import de.servicehealth.epa4all.server.idp.DiscoveryDocumentWrapper;
import de.servicehealth.epa4all.server.serviceport.ServicePortFile;
import io.quarkus.test.common.QuarkusTestResourceConfigurableLifecycleManager;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import static de.servicehealth.epa4all.common.TestUtils.getResourcePath;

@SuppressWarnings("rawtypes")
public class WiremockTestResource implements QuarkusTestResourceConfigurableLifecycleManager {

    private final File configFolder = getResourcePath("wiremock").toFile();

    // TODO refactor

    private final int httpsPort = 9443;

    @Override
    public Map<String, String> start() {
        try {
            ServicePortFile servicePortFile = new ServicePortFile(configFolder);
            servicePortFile.store(servicePortFile.changeEndpoints(Map.of("8070", httpsPort + "/konnektor")));

            DiscoveryDocumentFile<DiscoveryDocumentWrapper> documentFile = new DiscoveryDocumentFile<>(configFolder);
            documentFile.store(documentFile.load().changeEndpoints("8073", httpsPort + "/idp"));

        } catch (Exception e) {
            throw new RuntimeException("Failed to prepare cache file", e);
        }
        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        try {
            ServicePortFile servicePortFile = new ServicePortFile(configFolder);
            servicePortFile.store(servicePortFile.changeEndpoints(Map.of(httpsPort + "/konnektor", "8070")));

            DiscoveryDocumentFile<DiscoveryDocumentWrapper> documentFile = new DiscoveryDocumentFile<>(configFolder);
            documentFile.store(documentFile.load().changeEndpoints(httpsPort + "/idp", "8073"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to prepare cache file", e);
        }
    }
}