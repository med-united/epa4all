package de.servicehealth.epa4all.server.filetracker;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Getter
@ApplicationScoped
public class XdsConfig {

    @ConfigProperty(name = "xds.store.uploaded.files", defaultValue = "false")
    boolean storeUploadedFiles;
}
