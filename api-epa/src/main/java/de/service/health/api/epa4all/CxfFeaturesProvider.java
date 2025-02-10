package de.service.health.api.epa4all;

import de.service.health.api.epa4all.annotation.EpaRestFeatures;
import de.service.health.api.epa4all.annotation.EpaSoapFeatures;
import de.service.health.api.epa4all.annotation.KonnektorSoapFeatures;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.xml.ws.WebServiceFeature;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.ws.addressing.WSAddressingFeature;

import java.util.List;

@ApplicationScoped
public class CxfFeaturesProvider {

    @Inject
    ServicehealthConfig servicehealthConfig;

    @Produces
    @Singleton
    @EpaRestFeatures
    List<Feature> getRestFeatures() {
        return List.of(prepareLoggingFeature());
    }

    @Produces
    @Singleton
    @EpaSoapFeatures
    List<Feature> getEpaSoapFeatures() {
        return List.of(prepareLoggingFeature(), new WSAddressingFeature());
    }

    @Produces
    @Singleton
    @KonnektorSoapFeatures
    List<WebServiceFeature> getKonnektorSoapFeatures() {
        return List.of(prepareLoggingFeature());
    }

    private LoggingFeature prepareLoggingFeature() {
        LoggingFeature loggingFeature = new LoggingFeature();
        loggingFeature.setPrettyLogging(true);
        loggingFeature.setVerbose(true);
        loggingFeature.setLogMultipart(true);
        loggingFeature.setLogBinary(false);

        loggingFeature.setSensitiveElementNames(servicehealthConfig.getMaskedAttributes());
        loggingFeature.setSensitiveProtocolHeaderNames(servicehealthConfig.getMaskedHeaders());
        return loggingFeature;
    }
}
