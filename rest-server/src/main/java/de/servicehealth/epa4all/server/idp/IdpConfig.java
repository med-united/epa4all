package de.servicehealth.epa4all.server.idp;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Getter
@ApplicationScoped
public class IdpConfig {

    @ConfigProperty(name = "idp.client.id")
    String clientId;

    @ConfigProperty(name = "idp.service.url")
    String serviceUrl;

    @ConfigProperty(name = "idp.auth.request.url")
    String authRequestUrl;

    @ConfigProperty(name = "idp.auth.request.redirect.url")
    String authRequestRedirectUrl;

    public String getDiscoveryDocumentUrl() {
        return serviceUrl + "/.well-known/openid-configuration";
    }
}
