package de.service.health.api.epa4all;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Set;

@Getter
@ApplicationScoped
public class EpaConfig {

    @ConfigProperty(name = "epa.backend")
    Set<String> epaBackends;

    @ConfigProperty(name = "authorization-service.url")
    String authorizationServiceUrl;

    @ConfigProperty(name = "information-service.url")
    String informationServiceUrl;

    @ConfigProperty(name = "entitlement-service.url")
    String entitlementServiceUrl;

    @ConfigProperty(name = "medication-service.api.url")
    String medicationServiceApiUrl;

    @ConfigProperty(name = "medication-service.render.url")
    String medicationServiceRenderUrl;

    @ConfigProperty(name = "document-management-service.url")
    String documentManagementServiceUrl;

    @ConfigProperty(name = "document-management-insurant-service.url")
    String documentManagementInsurantServiceUrl;
}