package de.servicehealth.epa4all.server.serviceport;

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureServicePortType;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.CardServicePortType;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;
import de.gematik.ws.conn.vsds.vsdservice.v5_2.VSDServicePortType;
import de.health.service.config.api.IUserConfigurations;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;

import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class MultiKonnektorService {

    @Getter
    private final ConcurrentHashMap<KonnektorKey, IKonnektorAPI> portMap = new ConcurrentHashMap<>();

    private final ServicePortProvider servicePortProvider;

    @Inject
    public MultiKonnektorService(ServicePortProvider servicePortProvider) {
        this.servicePortProvider = servicePortProvider;
    }

    public IKonnektorAPI getServicePorts(IUserConfigurations userConfigurations) {
        return portMap.computeIfAbsent(new KonnektorKey(userConfigurations), kk -> {
            CardServicePortType cardServicePortType = servicePortProvider.getCardServicePortType(userConfigurations);
            EventServicePortType eventServicePort = servicePortProvider.getEventServicePort(userConfigurations);
            EventServicePortType eventServicePortSilent = servicePortProvider.getEventServicePortSilent(userConfigurations);
            VSDServicePortType vsdServicePortType = servicePortProvider.getVSDServicePortType(userConfigurations);
            CertificateServicePortType certificateService = servicePortProvider.getCertificateServicePort(userConfigurations);
            AuthSignatureServicePortType authSignatureService = servicePortProvider.getAuthSignatureServicePortType(userConfigurations);

            servicePortProvider.saveEndpointsConfiguration();

            return new KonnektorServicePorts(
                buildContextType(userConfigurations),
                cardServicePortType,
                eventServicePort,
                eventServicePortSilent,
                vsdServicePortType,
                certificateService,
                authSignatureService
            );
        });
    }

    public boolean isReady() {
        return servicePortProvider.getConfigDirectory() != null;
    }

    private ContextType buildContextType(IUserConfigurations userConfigurations) {
        ContextType contextType = new ContextType();
        contextType.setMandantId(userConfigurations.getMandantId());
        contextType.setClientSystemId(userConfigurations.getClientSystemId());
        contextType.setWorkplaceId(userConfigurations.getWorkplaceId());
        contextType.setUserId(userConfigurations.getUserId());
        return contextType;
    }

    // todo - cleanup ?
}
