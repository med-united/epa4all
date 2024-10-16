package de.servicehealth.epa4all.config;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

@Getter
@ApplicationScoped
public class KonnektorDefaultConfig {

    @ConfigProperty(name = "konnektor.default.url")
    String url;

    @ConfigProperty(name = "konnektor.default.version")
    String version;

    @ConfigProperty(name = "konnektor.default.mandant-id")
    String mandantId;

    @ConfigProperty(name = "konnektor.default.workplace-id")
    String workplaceId;

    @ConfigProperty(name = "konnektor.default.client-system-id")
    String clientSystemId;

    @ConfigProperty(name = "konnektor.default.user-id")
    Optional<String> userId;

    @ConfigProperty(name = "konnektor.default.tvMode")
    String tvMode;

    @ConfigProperty(name = "konnektor.default.cert.auth.store.file")
    Optional<String> certAuthStoreFile;

    @ConfigProperty(name = "konnektor.default.cert.auth.store.file.password")
    Optional<String> certAuthStoreFilePassword;
}