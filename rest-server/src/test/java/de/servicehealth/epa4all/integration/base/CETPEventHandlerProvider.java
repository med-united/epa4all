package de.servicehealth.epa4all.integration.base;

import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.cardlink.CardlinkClient;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.service.health.api.epa4all.EpaMultiService;
import de.servicehealth.epa4all.server.FeatureConfig;
import de.servicehealth.epa4all.server.cetp.CETPEventHandler;
import de.servicehealth.epa4all.server.config.DefaultUserConfig;
import de.servicehealth.epa4all.server.config.RuntimeConfig;
import de.servicehealth.epa4all.server.entitlement.EntitlementService;
import de.servicehealth.epa4all.server.epa.EpaCallGuard;
import de.servicehealth.epa4all.server.filetracker.download.EpaFileDownloader;
import de.servicehealth.epa4all.server.insurance.InsuranceDataService;
import de.servicehealth.epa4all.server.ws.WebSocketPayload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@SuppressWarnings("ConstantValue")
@ApplicationScoped
public class CETPEventHandlerProvider {

    @Inject
    jakarta.enterprise.event.Event<WebSocketPayload> webSocketPayloadEvent;

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


    public CETPEventHandler get(EpaFileDownloader mockDownloader, CardlinkClient cardlinkClient) {
        EpaFileDownloader downloader = epaFileDownloader != null ? mockDownloader : epaFileDownloader;
        RuntimeConfig runtimeConfig = new RuntimeConfig(konnektorDefaultConfig, defaultUserConfig.getUserConfigurations());
        return new CETPEventHandler(
            webSocketPayloadEvent, insuranceDataService, entitlementService, downloader, konnektorClient,
            epaMultiService, cardlinkClient, runtimeConfig, featureConfig, epaCallGuard
        );
    }
}