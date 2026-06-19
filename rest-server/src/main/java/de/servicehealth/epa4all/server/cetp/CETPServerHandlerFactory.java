package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.CETPEventHandlerFactory;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.servicehealth.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.server.FeatureConfig;
import de.servicehealth.epa4all.server.cetp.cardlink.CardlinkCetpHandler;
import de.servicehealth.epa4all.server.cetp.cardlink.CardlinkClientWSFactory;
import de.servicehealth.epa4all.server.cetp.popp.PoppCetpHandler;
import de.servicehealth.epa4all.server.cetp.popp.PoppConfig;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.entitlement.EntitlementService;
import de.servicehealth.epa4all.server.epa.EpaCallGuard;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.ws.payload.WsCetpPayload;
import de.servicehealth.epa4all.server.ws.payload.WsPoppPayload;
import de.servicehealth.epa4all.server.ws.payload.WsTelematikPayload;
import io.netty.channel.ChannelInboundHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

@ApplicationScoped
public class CETPServerHandlerFactory implements CETPEventHandlerFactory {

    private final PoppConfig poppConfig;
    private final EpaCallGuard epaCallGuard;
    private final FeatureConfig featureConfig;
    private final EpaMultiService epaMultiService;
    private final IKonnektorClient konnektorClient;
    private final EpaFileDownloader epaFileDownloader;
    private final EntitlementService entitlementService;
    private final InsuranceDataService insuranceDataService;
    private final CardlinkClientWSFactory cardlinkClientFactory;
    private final Event<WsPoppPayload> wsPoppPayloadEvent;
    private final Event<WsTelematikPayload> webSocketPayloadEvent;
    private final Event<WsCetpPayload> cetpPayloadEvent;
    private final KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    public CETPServerHandlerFactory(
        PoppConfig poppConfig,
        EpaCallGuard epaCallGuard,
        FeatureConfig featureConfig,
        EpaMultiService epaMultiService,
        IKonnektorClient konnektorClient,
        EpaFileDownloader epaFileDownloader,
        EntitlementService entitlementService,
        InsuranceDataService insuranceDataService,
        CardlinkClientWSFactory cardlinkClientFactory,
        Event<WsPoppPayload> wsPoppPayloadEvent,
        Event<WsTelematikPayload> webSocketPayloadEvent,
        Event<WsCetpPayload> cetpPayloadEvent,
        KonnektorDefaultConfig konnektorDefaultConfig
    ) {
        this.poppConfig = poppConfig;
        this.epaCallGuard = epaCallGuard;
        this.featureConfig = featureConfig;
        this.epaMultiService = epaMultiService;
        this.konnektorClient = konnektorClient;
        this.epaFileDownloader = epaFileDownloader;
        this.entitlementService = entitlementService;
        this.insuranceDataService = insuranceDataService;
        this.wsPoppPayloadEvent = wsPoppPayloadEvent;
        this.webSocketPayloadEvent = webSocketPayloadEvent;
        this.cetpPayloadEvent = cetpPayloadEvent;
        this.cardlinkClientFactory = cardlinkClientFactory;
        this.konnektorDefaultConfig = konnektorDefaultConfig;
    }

    @Override
    public ChannelInboundHandler[] build(KonnektorConfig konnektorConfig) {
        RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, konnektorConfig.getUserConfigurations());
        ChannelInboundHandler handler = switch (CETPHandlerType.from(poppConfig.getCetpFlow())) {
            case Cardlink -> {
                CardlinkClient cardlinkClient = cardlinkClientFactory.build(konnektorConfig);
                yield new CardlinkCetpHandler(
                    webSocketPayloadEvent, cetpPayloadEvent, insuranceDataService, entitlementService, epaFileDownloader,
                    konnektorClient, epaMultiService, cardlinkClient, runtimeConfig, featureConfig, epaCallGuard
                );
            }
            case Popp -> new PoppCetpHandler(
                poppConfig, wsPoppPayloadEvent, entitlementService, konnektorClient, runtimeConfig
            );
        };
        return new ChannelInboundHandler[] {handler};
    }
}
