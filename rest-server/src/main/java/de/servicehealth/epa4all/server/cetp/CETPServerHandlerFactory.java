package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.CETPEventHandlerFactory;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkWebsocketClient;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.service.health.api.epa4all.MultiEpaService;
import de.servicehealth.epa4all.server.config.AppConfig;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.smcb.WebdavSmcbManager;
import de.servicehealth.epa4all.server.vsds.VSDService;
import io.netty.channel.ChannelInboundHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CETPServerHandlerFactory implements CETPEventHandlerFactory {

    private final IdpClient idpClient;
    private final VSDService vsdService;
    private final WebdavSmcbManager smcbManager;
    private final MultiEpaService multiEpaService;
    private final IKonnektorClient konnektorClient;
    private final KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    public CETPServerHandlerFactory(
        IdpClient idpClient,
        VSDService vsdService,
        WebdavSmcbManager smcbManager,
        MultiEpaService multiEpaService,
        IKonnektorClient konnektorClient,
        KonnektorDefaultConfig konnektorDefaultConfig
    ) {
        this.idpClient = idpClient;
        this.vsdService = vsdService;
        this.smcbManager = smcbManager;
        this.multiEpaService = multiEpaService;
        this.konnektorClient = konnektorClient;
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
            cardlinkWebsocketClient, konnektorClient, multiEpaService, smcbManager, vsdService, appConfig
        );
        return new ChannelInboundHandler[] { cetpEventHandler };
    }
}
