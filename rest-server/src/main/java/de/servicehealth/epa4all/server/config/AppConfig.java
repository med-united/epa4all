package de.servicehealth.epa4all.server.config;

import de.health.service.cetp.CETPServer;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

@ApplicationScoped
@Getter
public class AppConfig  {

    @ConfigProperty(name = "connector.cetp.port")
    Optional<Integer> cetpPort;

    public int getCetpPort() {
        return cetpPort.orElse(CETPServer.DEFAULT_PORT);
    }
}
