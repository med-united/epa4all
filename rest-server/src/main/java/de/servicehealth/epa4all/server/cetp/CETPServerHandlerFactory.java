package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.CETPEventHandlerFactory;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.service.health.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.server.cetp.cardlink.CardlinkClientFactory;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.ws.CashierPayload;
import de.servicehealth.feature.FeatureConfig;
import io.netty.channel.ChannelInboundHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

@SuppressWarnings("CdiInjectionPointsInspection")
@ApplicationScoped
public class CETPServerHandlerFactory implements CETPEventHandlerFactory {

    private final FeatureConfig featureConfig;
    private final VauNpProvider vauNpProvider;
    private final EpaMultiService epaMultiService;
    private final IKonnektorClient konnektorClient;
    private final EpaFileDownloader epaFileDownloader;
    private final Event<CashierPayload> cashierPayloadEvent;
    private final InsuranceDataService insuranceDataService;
    private final CardlinkClientFactory cardlinkClientFactory;
    private final KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    public CETPServerHandlerFactory(
        FeatureConfig featureConfig,
        VauNpProvider vauNpProvider,
        EpaMultiService epaMultiService,
        IKonnektorClient konnektorClient,
        EpaFileDownloader epaFileDownloader,
        Event<CashierPayload> cashierPayloadEvent,
        InsuranceDataService insuranceDataService,
        CardlinkClientFactory cardlinkClientFactory,
        KonnektorDefaultConfig konnektorDefaultConfig
    ) {
        this.featureConfig = featureConfig;
        this.vauNpProvider = vauNpProvider;
        this.epaMultiService = epaMultiService;
        this.konnektorClient = konnektorClient;
        this.epaFileDownloader = epaFileDownloader;
        this.cashierPayloadEvent = cashierPayloadEvent;
        this.insuranceDataService = insuranceDataService;
        this.cardlinkClientFactory = cardlinkClientFactory;
        this.konnektorDefaultConfig = konnektorDefaultConfig;
    }

    @Override
    public ChannelInboundHandler[] build(KonnektorConfig konnektorConfig) {
        RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, konnektorConfig.getUserConfigurations());
        CardlinkClient cardlinkWebsocketClient = cardlinkClientFactory.build(konnektorConfig);
        CETPEventHandler cetpEventHandler = new CETPEventHandler(
            cashierPayloadEvent, cardlinkWebsocketClient, insuranceDataService, epaFileDownloader,
            konnektorClient, epaMultiService, vauNpProvider, runtimeConfig, featureConfig
        );
        return new ChannelInboundHandler[] { cetpEventHandler };
    }
}
