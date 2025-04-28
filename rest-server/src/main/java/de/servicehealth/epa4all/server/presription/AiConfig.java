package de.servicehealth.epa4all.server.presription;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Getter
@ApplicationScoped
public class AiConfig {

    @ConfigProperty(name = "ai.search.url")
    String searchUrl;
}