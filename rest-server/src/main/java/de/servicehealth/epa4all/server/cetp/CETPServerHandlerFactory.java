package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.CETPEventHandlerFactory;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkWebsocketClient;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.service.health.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.idp.vaunp.VauNpProvider;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.vsd.VsdConfig;
import io.netty.channel.ChannelInboundHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CETPServerHandlerFactory implements CETPEventHandlerFactory {

    private final VsdConfig vsdConfig;
    private final IdpClient idpClient;
    private final VauNpProvider vauNpProvider;
    private final EpaMultiService epaMultiService;
    private final IKonnektorClient konnektorClient;
    private final EpaFileDownloader epaFileDownloader;
    private final InsuranceDataService insuranceDataService;
    private final KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    public CETPServerHandlerFactory(
        IdpClient idpClient,
        VsdConfig vsdConfig,
        VauNpProvider vauNpProvider,
        EpaMultiService epaMultiService,
        IKonnektorClient konnektorClient,
        EpaFileDownloader epaFileDownloader,
        InsuranceDataService insuranceDataService,
        KonnektorDefaultConfig konnektorDefaultConfig
    ) {
        this.idpClient = idpClient;
        this.vsdConfig = vsdConfig;
        this.vauNpProvider = vauNpProvider;
        this.epaMultiService = epaMultiService;
        this.konnektorClient = konnektorClient;
        this.epaFileDownloader = epaFileDownloader;
        this.insuranceDataService = insuranceDataService;
        this.konnektorDefaultConfig = konnektorDefaultConfig;
    }

    @Override
    public ChannelInboundHandler[] build(KonnektorConfig konnektorConfig) {
        RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, konnektorConfig.getUserConfigurations());
        CardlinkWebsocketClient cardlinkWebsocketClient = new CardlinkWebsocketClient(
            konnektorConfig.getCardlinkEndpoint(),
            new EpaJwtConfigurator(runtimeConfig, idpClient)
        );
        CETPEventHandler cetpEventHandler = new CETPEventHandler(
            cardlinkWebsocketClient, insuranceDataService, epaFileDownloader,
            konnektorClient, epaMultiService, vauNpProvider, runtimeConfig, vsdConfig
        );
        return new ChannelInboundHandler[] { cetpEventHandler };
    }
}
