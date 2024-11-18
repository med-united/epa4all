package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.CETPEventHandlerFactory;
import de.health.service.cetp.cardlink.CardlinkWebsocketClient;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.server.config.AppConfig;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import io.netty.channel.ChannelInboundHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CETPServerHandlerFactory implements CETPEventHandlerFactory {

    private final IdpClient idpClient;
    private final MultiEpaService multiEpaService;
    private final InsuranceDataService insuranceDataService;
    private final KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    public CETPServerHandlerFactory(
        IdpClient idpClient,
        MultiEpaService multiEpaService,
        InsuranceDataService insuranceDataService,
        KonnektorDefaultConfig konnektorDefaultConfig
    ) {
        this.idpClient = idpClient;
        this.multiEpaService = multiEpaService;
        this.insuranceDataService = insuranceDataService;
        this.konnektorDefaultConfig = konnektorDefaultConfig;
    }

    @Override
    public ChannelInboundHandler[] build(KonnektorConfig konnektorConfig) {
        AppConfig appConfig = new AppConfig(konnektorDefaultConfig, konnektorConfig.getUserConfigurations());
        CardlinkWebsocketClient cardlinkWebsocketClient = new CardlinkWebsocketClient(
            konnektorConfig.getCardlinkEndpoint(),
            new EpaJwtConfigurator(appConfig, idpClient)
        );
        CETPEventHandler cetpEventHandler = new CETPEventHandler(
            cardlinkWebsocketClient, insuranceDataService, multiEpaService, appConfig
        );
        return new ChannelInboundHandler[] { cetpEventHandler };
    }
}
