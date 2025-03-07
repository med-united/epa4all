package de.servicehealth.epa4all.server.jcr;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;

@Getter
@ApplicationScoped
public class JcrConfig {

    @ConfigProperty(name = "jcr.repository.home")
    File repositoryHome;

    public String getWorkspacesHome() {
        return repositoryHome.getAbsolutePath() + "/workspaces";
    }
}