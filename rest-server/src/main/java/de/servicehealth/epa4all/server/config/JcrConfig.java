package de.servicehealth.epa4all.server.config;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Getter
@ApplicationScoped
public class JcrConfig {

    @ConfigProperty(name = "jcr.repository.home")
    String repositoryHome;
}
