package de.servicehealth.epa4all.integration.bc.wiremock;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.cetp.config.KonnektorConfig;
import de.servicehealth.epa4all.common.profile.WireMockProfile;
import de.servicehealth.epa4all.integration.base.AbstractWiremockTest;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@QuarkusTest
@TestProfile(WireMockProfile.class)
public class CardInsertedPdfFailedEpaIT extends AbstractWiremockTest {

    private final String kvnr = "X110587452";

    @ConfigProperty(name = "ere.per.konnektor.config.folder")
    String configFolder;

    @Test
    public void epaPdfWasNotSentToCardlink() throws Exception {
        String ctId = "cardTerminal-124";


        CardlinkClient cardlinkClient = mock(CardlinkClient.class);
        EpaFileDownloader epaFileDownloader = mock(EpaFileDownloader.class);
        KonnektorConfig konnektorConfig = konnektorConfigs.values().iterator().next();

        prepareIdpStubs();
        prepareVauStubs();
        prepareKonnektorStubs();
        prepareInformationStubs(204);

        String egkHandle = konnektorClient.getEgkHandle(defaultUserConfig, kvnr);

        receiveCardInsertedEvent(
            konnektorConfig,
            epaFileDownloader,
            cardlinkClient,
            egkHandle,
            ctId
        );
        verify(epaFileDownloader, never()).handleDownloadResponse(any(), any());
    }

    @AfterEach
    public void afterEachEx() {
        QuarkusMock.installMockForType(epaFileDownloader, EpaFileDownloader.class);
        QuarkusMock.installMockForType(konnektorClient, IKonnektorClient.class);
    }
}

