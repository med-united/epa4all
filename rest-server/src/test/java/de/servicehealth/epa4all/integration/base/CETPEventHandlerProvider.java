package de.servicehealth.epa4all.integration.base;

import de.health.service.cetp.AbstractCETPEventHandler;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.servicehealth.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.server.FeatureConfig;
import de.servicehealth.epa4all.server.cetp.CETPHandlerType;
import de.servicehealth.epa4all.server.cetp.cardlink.CardlinkCetpHandler;
import de.servicehealth.epa4all.server.cetp.popp.PoppCetpHandler;
import de.servicehealth.epa4all.server.cetp.popp.PoppConfig;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.entitlement.EntitlementService;
import de.servicehealth.epa4all.server.epa.EpaCallGuard;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.ws.payload.WsCetpPayload;
import de.servicehealth.epa4all.server.ws.payload.WsPoppPayload;
import de.servicehealth.epa4all.server.ws.payload.WsTelematikPayload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import static org.mockito.Mockito.mock;

@SuppressWarnings("ConstantValue")
@ApplicationScoped
public class CETPEventHandlerProvider {

    @Inject
    Event<WsTelematikPayload> webSocketPayloadEvent;

    @Inject
    Event<WsCetpPayload> cetpPayloadEvent;

    @Inject
    Event<WsPoppPayload> wsPoppPayloadEvent;

    @Inject
    protected PoppConfig poppConfig;

    @Inject
    protected KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    protected InsuranceDataService insuranceDataService;

    @Inject
    protected EntitlementService entitlementService;

    @Inject
    protected DefaultUserConfig defaultUserConfig;

    @Inject
    protected EpaFileDownloader epaFileDownloader;

    @Inject
    protected IKonnektorClient konnektorClient;

    @Inject
    protected EpaMultiService epaMultiService;

    @Inject
    protected FeatureConfig featureConfig;

    @Inject
    protected EpaCallGuard epaCallGuard;

    public AbstractCETPEventHandler get(
        EpaFileDownloader mockDownloader,
        FeatureConfig mockFeatureConfig
    ) {
        EpaFileDownloader downloader = epaFileDownloader != null ? mockDownloader : epaFileDownloader;
        FeatureConfig featureCfg = mockFeatureConfig != null ? mockFeatureConfig : featureConfig;
        RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, defaultUserConfig.getUserConfigurations());

        return switch (CETPHandlerType.from(poppConfig.getCetpFlow())) {
            case Cardlink -> {
                CardlinkClient cardlinkClient = mock(CardlinkClient.class);
                yield new CardlinkCetpHandler(
                    webSocketPayloadEvent, cetpPayloadEvent, insuranceDataService, entitlementService, downloader,
                    konnektorClient, epaMultiService, cardlinkClient, runtimeConfig, featureCfg, epaCallGuard
                );
            }
            case Popp -> new PoppCetpHandler(
                poppConfig, wsPoppPayloadEvent, entitlementService, konnektorClient, runtimeConfig
            );
        };
    }
}
