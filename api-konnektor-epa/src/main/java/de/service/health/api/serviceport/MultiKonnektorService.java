package de.service.health.api.serviceport;

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureServicePortType;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.CardServicePortType;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;
import de.gematik.ws.conn.vsds.vsdservice.v5_2.VSDServicePortType;
import de.servicehealth.config.api.UserRuntimeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class MultiKonnektorService {

    private final ConcurrentHashMap<KonnektorKey, IKonnektorServicePortsAPI> portMap = new ConcurrentHashMap<>();

    private final KServicePortProvider servicePortProvider;

    @Inject
    public MultiKonnektorService(KServicePortProvider servicePortProvider) {
        this.servicePortProvider = servicePortProvider;
    }

    public IKonnektorServicePortsAPI getServicePorts(UserRuntimeConfig userRuntimeConfig) {
        return portMap.computeIfAbsent(new KonnektorKey(userRuntimeConfig), kk -> {
            CardServicePortType cardServicePortType = servicePortProvider.getCardServicePortType(userRuntimeConfig);
            EventServicePortType eventServicePort = servicePortProvider.getEventServicePort(userRuntimeConfig);
            VSDServicePortType vsdServicePortType = servicePortProvider.getVSDServicePortType(userRuntimeConfig);
            CertificateServicePortType certificateService = servicePortProvider.getCertificateServicePort(userRuntimeConfig);
            AuthSignatureServicePortType authSignatureService = servicePortProvider.getAuthSignatureServicePortType(userRuntimeConfig);
            return new KonnektorServicePortAggregator(
                buildContextType(userRuntimeConfig),
                cardServicePortType,
                eventServicePort,
                vsdServicePortType,
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