package de.servicehealth.epa4all.server;

import de.health.service.config.api.IFeatureConfig;
import de.servicehealth.feature.EpaFeatureConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Getter
@ApplicationScoped
public class FeatureConfig implements IFeatureConfig, EpaFeatureConfig {

    @ConfigProperty(name = "feature.mutual.tls.enabled", defaultValue = "false")
    boolean mutualTlsEnabled;

    @ConfigProperty(name = "feature.cetp.enabled", defaultValue = "true")
    boolean cetpEnabled;

    @ConfigProperty(name = "feature.cardlink.enabled", defaultValue = "false")
    boolean cardlinkEnabled;

    @ConfigProperty(name = "feature.native-fhir.enabled", defaultValue = "false")
    boolean nativeFhirEnabled;

    @ConfigProperty(name = "feature.external-pnw.enabled", defaultValue = "false")
    boolean externalPnwEnabled;
}
