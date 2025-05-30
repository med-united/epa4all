package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.CETPEventHandlerFactory;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.servicehealth.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.server.FeatureConfig;
import de.servicehealth.epa4all.server.cetp.cardlink.CardlinkClientWSFactory;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.entitlement.EntitlementService;
import de.servicehealth.epa4all.server.epa.EpaCallGuard;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.ws.WebSocketPayload;
import io.netty.channel.ChannelInboundHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

@ApplicationScoped
public class CETPServerHandlerFactory implements CETPEventHandlerFactory {

    private final EpaCallGuard epaCallGuard;
    private final FeatureConfig featureConfig;
    private final EpaMultiService epaMultiService;
    private final IKonnektorClient konnektorClient;
    private final EpaFileDownloader epaFileDownloader;
    private final EntitlementService entitlementService;
    private final InsuranceDataService insuranceDataService;
    private final CardlinkClientWSFactory cardlinkClientFactory;
    private final Event<WebSocketPayload> webSocketPayloadEvent;
    private final KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    public CETPServerHandlerFactory(
        EpaCallGuard epaCallGuard,
        FeatureConfig featureConfig,
        EpaMultiService epaMultiService,
        IKonnektorClient konnektorClient,
        EpaFileDownloader epaFileDownloader,
        EntitlementService entitlementService,
        InsuranceDataService insuranceDataService,
        CardlinkClientWSFactory cardlinkClientFactory,
        Event<WebSocketPayload> webSocketPayloadEvent,
        KonnektorDefaultConfig konnektorDefaultConfig
    ) {
        this.epaCallGuard = epaCallGuard;
        this.featureConfig = featureConfig;
        this.epaMultiService = epaMultiService;
        this.konnektorClient = konnektorClient;
        this.epaFileDownloader = epaFileDownloader;
        this.entitlementService = entitlementService;
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
            webSocketPayloadEvent, insuranceDataService, entitlementService, epaFileDownloader, konnektorClient,
            epaMultiService, cardlinkClient, runtimeConfig, featureConfig, epaCallGuard
        );
        return new ChannelInboundHandler[] { cetpEventHandler };
    }
}
