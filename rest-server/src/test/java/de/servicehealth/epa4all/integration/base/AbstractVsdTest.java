package de.servicehealth.epa4all.integration.base;

import de.gematik.ws.conn.eventservice.v7.Event;
import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.KonnektorsConfigs;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.cetp.domain.eventservice.event.DecodeResult;
import de.health.service.config.api.IUserConfigurations;
import de.service.health.api.epa4all.EpaConfig;
import de.service.health.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.server.cetp.CETPEventHandler;
import de.servicehealth.epa4all.server.cetp.KonnektorClient;
import de.servicehealth.epa4all.server.cetp.mapper.event.EventMapper;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.entitlement.EntitlementService;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.vsd.VsdService;
import de.servicehealth.feature.FeatureConfig;
import io.netty.channel.embedded.EmbeddedChannel;
import io.quarkus.test.junit.QuarkusMock;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("UnusedReturnValue")
public abstract class AbstractVsdTest {

    private static final Logger log = Logger.getLogger(AbstractVsdTest.class.getName());

    public static final String INFORMATION_SERVICE = "information-service";
    public static final String VAU_PROXY_SERVER = "vau-proxy-server";
    public static final String ENTITLEMENT_SERVICE = "entitlement-service";
    public static final String MEDICATION_RENDER_SERVICE = "medication-render-service";

    public static final File TEST_FOLDER = new File("test-data");

    @Inject
    protected EpaConfig epaConfig;

    @Inject
    protected VsdService vsdService;

    @Inject
    protected EventMapper eventMapper;

    @Inject
    protected WebdavConfig webdavConfig;

    @Inject
    protected FeatureConfig featureConfig;

    @Inject
    protected FolderService folderService;

    @Inject
    protected VauNpProvider vauNpProvider;

    @Inject
    protected EpaMultiService epaMultiService;

    @Inject
    protected IKonnektorClient konnektorClient;

    @Inject
    protected DefaultUserConfig defaultUserConfig;

    @Inject
    protected EpaFileDownloader epaFileDownloader;

    @Inject
    protected EntitlementService entitlementService;

    @Inject
    protected InsuranceDataService insuranceDataService;

    @Inject
    protected KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    @KonnektorsConfigs
    protected Map<String, KonnektorConfig> konnektorConfigs;


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
                        log.log(Level.SEVERE, e.getMessage());
                    }
                } else {
                    f.delete();
                }
            });
        }
    }

    protected ReadVSDResponse prepareReadVSDResponse() throws Exception {
        String xml = "<PN CDM_VERSION=\"1.0.0\" xmlns=\"http://ws.gematik.de/fa/vsdm/pnw/v1.0\"><TS>20241121115318</TS><E>2</E><PZ>WDExMDQ4NTI5MTE3MzIxODk5OTdVWDFjxzDPSFvdIrRmmmOWFP/aP5rakVUqQj8=</PZ></PN>";
        return VsdService.buildSyntheticVSDResponse(xml, null);
    }

    protected WebdavConfig mockWebdavConfig() {
        WebdavConfig webdavConfig = mock(WebdavConfig.class);
        when(webdavConfig.getRootFolder()).thenReturn(TEST_FOLDER.getAbsolutePath());
        when(webdavConfig.getSmcbFolders()).thenReturn(
            Set.of(
                "eab_2ed345b1-35a3-49e1-a4af-d71ca4f23e57",
                "other_605a9f3c-bfe8-4830-a3e3-25a4ec6612cb",
                "local_00000000-0000-0000-0000-000000000000"
            )
        );
        QuarkusMock.installMockForType(webdavConfig, WebdavConfig.class);
        return webdavConfig;
    }

    protected KonnektorClient mockKonnectorClient(
        String egkHandle,
        String telematikId,
        String kvnr,
        String smcbHandle
    ) throws Exception {
        KonnektorClient konnektorClientMock = mock(KonnektorClient.class);
        when(konnektorClientMock.getEgkHandle(any(), any())).thenReturn(egkHandle);
        when(konnektorClientMock.getTelematikId(any(), any())).thenReturn(telematikId);
        when(konnektorClientMock.getKvnr(any(), any())).thenReturn(kvnr);
        when(konnektorClientMock.getSmcbHandle(any())).thenReturn(smcbHandle);
        when(konnektorClientMock
            .getSmcbX509Certificate(any(), eq(smcbHandle)))
            .thenReturn(Pair.of(mock(X509Certificate.class), false));

        QuarkusMock.installMockForType(konnektorClientMock, IKonnektorClient.class);
        return konnektorClientMock;
    }

    protected VsdService mockVsdService() throws Exception {
        VsdService vsdServiceMock = mock(VsdService.class);
        ReadVSDResponse readVSDResponse = prepareReadVSDResponse();
        when(vsdServiceMock.readVsd(any(), any(), any())).thenReturn(readVSDResponse);
        QuarkusMock.installMockForType(vsdServiceMock, VsdService.class);
        return vsdServiceMock;
    }

    protected VauNpProvider mockVauNpProvider() {
        VauNpProvider vauNpProviderMock = mock(VauNpProvider.class);
        when(vauNpProviderMock.getVauNp(any(), any(), any())).thenReturn("f5931e8c19c21bca44fa");
        QuarkusMock.installMockForType(vauNpProviderMock, VauNpProvider.class);
        return vauNpProviderMock;
    }

    protected KonnektorConfig mockKonnektorConfig() {
        KonnektorConfig konnektorConfig = mock(KonnektorConfig.class);
        IUserConfigurations configurations = mock(IUserConfigurations.class);
        when(konnektorConfig.getUserConfigurations()).thenReturn(configurations);
        return konnektorConfig;
    }

    protected void receiveCardInsertedEvent(
        KonnektorConfig konnektorConfig,
        VauNpProvider vauNpProvider,
        CardlinkClient cardlinkClient,
        String egkHandle
    ) {
        RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, defaultUserConfig.getUserConfigurations());

        CETPEventHandler cetpServerHandler = new CETPEventHandler(
            cardlinkClient, insuranceDataService, epaFileDownloader, konnektorClient,
            epaMultiService, vauNpProvider, runtimeConfig, featureConfig
        );
        EmbeddedChannel channel = new EmbeddedChannel(cetpServerHandler);

        String slotIdValue = "3";
        String ctIdValue = "CtIDValue";

        channel.writeOneInbound(decode(konnektorConfig, slotIdValue, ctIdValue, egkHandle));
        channel.pipeline().fireChannelReadComplete();
    }

    protected DecodeResult decode(
        KonnektorConfig konnektorConfig,
        String slotIdValue,
        String ctIdValue,
        String egkHandle
    ) {
        Event event = new Event();
        event.setTopic("CARD/INSERTED");
        Event.Message message = new Event.Message();
        Event.Message.Parameter parameter = new Event.Message.Parameter();
        parameter.setKey("CardHandle");
        parameter.setValue(egkHandle);
        Event.Message.Parameter parameterSlotId = new Event.Message.Parameter();
        parameterSlotId.setKey("SlotID");
        parameterSlotId.setValue(slotIdValue);
        Event.Message.Parameter parameterCtId = new Event.Message.Parameter();
        parameterCtId.setKey("CtID");
        parameterCtId.setValue(ctIdValue);
        Event.Message.Parameter parameterCardType = new Event.Message.Parameter();
        parameterCardType.setKey("CardType");
        parameterCardType.setValue("EGK");

        message.getParameter().add(parameter);
        message.getParameter().add(parameterSlotId);
        message.getParameter().add(parameterCtId);
        message.getParameter().add(parameterCardType);
        event.setMessage(message);
        return new DecodeResult(eventMapper.toDomain(event), konnektorConfig.getUserConfigurations());
    }
}
