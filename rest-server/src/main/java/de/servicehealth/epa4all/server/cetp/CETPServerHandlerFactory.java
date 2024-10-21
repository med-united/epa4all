package de.servicehealth.epa4all.server.cetp;

import de.health.service.cetp.CETPEventHandlerFactory;
import de.health.service.cetp.cardlink.CardlinkWebsocketClient;
import de.servicehealth.config.KonnektorConfig;
import de.servicehealth.config.KonnektorDefaultConfig;
import de.servicehealth.config.api.IRuntimeConfig;
import de.servicehealth.config.api.IUserConfigurations;
import de.servicehealth.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.idp.IdpClient;
import de.servicehealth.epa4all.medication.service.DocService;
import de.servicehealth.epa4all.server.config.AppConfig;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.pharmacy.PharmacyService;
import io.netty.channel.ChannelInboundHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CETPServerHandlerFactory implements CETPEventHandlerFactory {

    private final IdpClient idpClient;
    private final DocService docService;
    private final PharmacyService pharmacyService;
    private final DefaultUserConfig defaultUserConfig;
    private final KonnektorDefaultConfig konnektorDefaultConfig;

    @Inject
    public CETPServerHandlerFactory(
        IdpClient idpClient,
        DocService docService,
        PharmacyService pharmacyService,
        DefaultUserConfig defaultUserConfig,
        KonnektorDefaultConfig konnektorDefaultConfig
    ) {
        this.idpClient = idpClient;
        this.docService = docService;
        this.pharmacyService = pharmacyService;
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
            new CETPEventHandler(cardlinkWebsocketClient, defaultUserConfig, pharmacyService, docService)
        };
    }
}
