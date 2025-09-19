package de.servicehealth.epa4all.server.idp;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Getter
@ApplicationScoped
public class TssIdpConfig {

    @ConfigProperty(name = "tss.idp.client.id")
    String clientId;

    @ConfigProperty(name = "tss.idp.service.url")
    String serviceUrl;

    @ConfigProperty(name = "tss.idp.auth.request.url")
    String authRequestUrl;

    @ConfigProperty(name = "tss.idp.auth.request.redirect.url")
    String authRequestRedirectUrl;

    @ConfigProperty(name = "tss.idp.hcv.enabled", defaultValue = "false")
    boolean hcvEnabled;
}