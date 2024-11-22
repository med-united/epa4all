package de.servicehealth.epa4all.server.serviceport;

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureServicePortType;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.CardServicePortType;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;
import de.gematik.ws.conn.vsds.vsdservice.v5_2.VSDServicePortType;
import de.health.service.config.api.UserRuntimeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class MultiKonnektorService {

    private static final Logger log = Logger.getLogger(MultiKonnektorService.class.getName());

    @Getter
    private final ConcurrentHashMap<KonnektorKey, IKonnektorServicePortsAPI> portMap = new ConcurrentHashMap<>();

    private final KServicePortProvider servicePortProvider;

    @Setter
    @ConfigProperty(name = "ere.per.konnektor.config.folder")
    String configFolder;

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

            try {
                Map<String, Map<String, String>> map = servicePortProvider.getUserConfigurations2endpointMap();
                new ServicePortFile(new File(configFolder)).store(map);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Error while saving service-ports file");
            }

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
