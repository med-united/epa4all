package de.servicehealth.epa4all.server.kim;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Getter
@ApplicationScoped
public class SmtpConfig {

    @ConfigProperty(name = "smtp.server")
    String server;

    @ConfigProperty(name = "smtp.port")
    String port;

    @ConfigProperty(name = "smtp.user")
    String user;

    @ConfigProperty(name = "smtp.authentication")
    String authentication;

    @ConfigProperty(name = "smtp.password")
    String password;

    @ConfigProperty(name = "smtp.auth")
    boolean auth;

    @ConfigProperty(name = "smtp.starttls.enable")
    boolean starttls;
}