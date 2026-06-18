package de.servicehealth.epa4all.server.rest.filter;

import de.servicehealth.epa4all.server.cdi.TelematikIdLiteral;
import de.servicehealth.feature.EpaFeatureConfig;
import io.quarkus.security.AuthenticationFailedException;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSession;
import java.security.cert.X509Certificate;
import java.util.stream.Stream;

@Provider
@ApplicationScoped
public class ClientCertificateFilter implements ContainerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ClientCertificateFilter.class.getName());

    @Inject
    EpaFeatureConfig featureConfig;

    @Inject
    RoutingContext routingContext;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (featureConfig.isMutualTlsEnabled()) {
            AuthenticationFailedException unauthorized = new AuthenticationFailedException("Unauthorized");
            try {
                HttpServerRequest httpRequest = routingContext.request();
                SSLSession sslSession = httpRequest.sslSession();
                X509Certificate[] clientCerts = (X509Certificate[]) sslSession.getPeerCertificates();
                if (clientCerts != null && clientCerts.length > 0) {
                    X509Certificate clientCert = clientCerts[0];
                    String subjectDN = clientCert.getSubjectX500Principal().getName();
                    String cnTelematikId = Stream.of(subjectDN.split(","))
                        .filter(p -> p.startsWith("CN"))
                        .findFirst()
                        .map(p -> p.split("=")[1].trim())
                        .orElseThrow(() -> unauthorized);

                    String telematikId = CDI.current()
                        .select(String.class, new TelematikIdLiteral())
                        .get();

                    if (!cnTelematikId.equalsIgnoreCase(telematikId)) {
                        throw unauthorized;
                    }
                } else {
                    throw unauthorized;
                }
            } catch (Exception e) {
                log.error("Error while authenticating client request", e);
                if (e instanceof AuthenticationFailedException authenticationFailedException) {
                    throw authenticationFailedException;
                } else {
                    throw new AuthenticationFailedException(e);
                }
            }
        }
    }
}
