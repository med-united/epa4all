package de.servicehealth.epa4all.serviceport;

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureServicePortType;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.CardServicePortType;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;
import de.servicehealth.config.KonnektorConfig;
import de.servicehealth.config.KonnektorDefaultConfig;
import de.servicehealth.config.api.IUserConfigurations;
import de.servicehealth.config.api.UserRuntimeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class MultiKonnektorService {

    private final ConcurrentHashMap<KonnektorKey, IServicePortAggregator> portMap = new ConcurrentHashMap<>();

    private final ServicePortProvider servicePortProvider;

    @Inject
    public MultiKonnektorService(ServicePortProvider servicePortProvider) {
        this.servicePortProvider = servicePortProvider;
    }

    public IServicePortAggregator getServicePorts(UserRuntimeConfig userRuntimeConfig) {
        return portMap.computeIfAbsent(new KonnektorKey(userRuntimeConfig), kk -> {
            CardServicePortType cardServicePortType = servicePortProvider.getCardServicePortType(userRuntimeConfig);
            EventServicePortType eventServicePort = servicePortProvider.getEventServicePort(userRuntimeConfig);
            CertificateServicePortType certificateService = servicePortProvider.getCertificateServicePort(userRuntimeConfig);
            AuthSignatureServicePortType authSignatureService = servicePortProvider.getAuthSignatureServicePortType(userRuntimeConfig);
            return new ServicePortAggregator(
                buildContextType(userRuntimeConfig),
                cardServicePortType,
                eventServicePort,
                certificateService,
                authSignatureService
            );
        });
    }

    private ContextType buildContextType(UserRuntimeConfig userRuntimeConfig) {
        ContextType contextType = new ContextType();
        contextType.setMandantId(userRuntimeConfig.getMandantId());
        contextType.setClientSystemId(userRuntimeConfig.getClientSystemId());
        contextType.setWorkplaceId(userRuntimeConfig.getWorkplaceId());
        contextType.setUserId(userRuntimeConfig.getUserId());
        return contextType;
    }

    // todo - cleanup ?
}
