package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.CETPEventHandlerFactory;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.service.health.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.server.cetp.cardlink.CardlinkClientFactory;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.epa.EpaCallGuard;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.ws.WebSocketPayload;
import de.servicehealth.feature.FeatureConfig;
import io.netty.channel.ChannelInboundHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

@SuppressWarnings("CdiInjectionPointsInspection")
@ApplicationScoped
public class CETPServerHandlerFactory implements CETPEventHandlerFactory {

    private final EpaCallGuard epaCallGuard;
    private final FeatureConfig featureConfig;
    private final VauNpProvider vauNpProvider;
    private final EpaMultiService epaMultiService;
    private final IKonnektorClient konnektorClient;
    private final EpaFileDownloader epaFileDownloader;
    private final InsuranceDataService insuranceDataService;
    private final CardlinkClientFactory cardlinkClientFactory;
    private final Event<WebSocketPayload> webSocketPayloadEvent;
    private final KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    public CETPServerHandlerFactory(
        EpaCallGuard epaCallGuard,
        FeatureConfig featureConfig,
        VauNpProvider vauNpProvider,
        EpaMultiService epaMultiService,
        IKonnektorClient konnektorClient,
        EpaFileDownloader epaFileDownloader,
        InsuranceDataService insuranceDataService,
        CardlinkClientFactory cardlinkClientFactory,
        Event<WebSocketPayload> webSocketPayloadEvent,
        KonnektorDefaultConfig konnektorDefaultConfig
    ) {
        this.epaCallGuard = epaCallGuard;
        this.featureConfig = featureConfig;
        this.vauNpProvider = vauNpProvider;
        this.epaMultiService = epaMultiService;
        this.konnektorClient = konnektorClient;
        this.epaFileDownloader = epaFileDownloader;
        this.insuranceDataService = insuranceDataService;
        this.webSocketPayloadEvent = webSocketPayloadEvent;
        this.cardlinkClientFactory = cardlinkClientFactory;
        this.konnektorDefaultConfig = konnektorDefaultConfig;
    }

    @Override
    public ChannelInboundHandler[] build(KonnektorConfig konnektorConfig) {
        RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, konnektorConfig.getUserConfigurations());
        CardlinkClient cardlinkClient = cardlinkClientFactory.build(konnektorConfig);
        CETPEventHandler cetpEventHandler = new CETPEventHandler(
            webSocketPayloadEvent, insuranceDataService, epaFileDownloader, konnektorClient,
            epaMultiService, cardlinkClient, vauNpProvider, runtimeConfig, featureConfig, epaCallGuard
        );
        return new ChannelInboundHandler[] { cetpEventHandler };
    }
}
