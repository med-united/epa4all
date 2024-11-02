package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.CETPEventHandlerFactory;
import de.health.service.cetp.cardlink.CardlinkWebsocketClient;
import de.service.health.api.epa4all.MultiEpaService;
import de.health.service.cetp.config.KonnektorConfig;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.servicehealth.epa4all.server.idp.IdpClient;
import de.servicehealth.epa4all.server.config.AppConfig;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.smcb.SmcbManager;
import de.servicehealth.epa4all.server.vsds.VSDService;
import io.netty.channel.ChannelInboundHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CETPServerHandlerFactory implements CETPEventHandlerFactory {

    private final IdpClient idpClient;
    private final VSDService vsdService;
    private final SmcbManager smcbManager;
    private final MultiEpaService multiEpaService;
    private final DefaultUserConfig defaultUserConfig;
    private final KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    public CETPServerHandlerFactory(
        IdpClient idpClient,
        VSDService vsdService,
        SmcbManager smcbManager,
        MultiEpaService multiEpaService,
        DefaultUserConfig defaultUserConfig,
        KonnektorDefaultConfig konnektorDefaultConfig
    ) {
        this.idpClient = idpClient;
        this.vsdService = vsdService;
        this.smcbManager = smcbManager;
        this.multiEpaService = multiEpaService;
        this.defaultUserConfig = defaultUserConfig;
        this.konnektorDefaultConfig = konnektorDefaultConfig;
    }

    @Override
    public ChannelInboundHandler[] build(KonnektorConfig konnektorConfig) {
        AppConfig userRuntimeConfig = new AppConfig(konnektorDefaultConfig, konnektorConfig.getUserConfigurations());
        CardlinkWebsocketClient cardlinkWebsocketClient = new CardlinkWebsocketClient(
            konnektorConfig.getCardlinkEndpoint(),
            new EpaJwtConfigurator(userRuntimeConfig, idpClient)
        );
        return new ChannelInboundHandler[] {
            new CETPEventHandler(cardlinkWebsocketClient, defaultUserConfig, multiEpaService, smcbManager, vsdService)
        };
    }
}
