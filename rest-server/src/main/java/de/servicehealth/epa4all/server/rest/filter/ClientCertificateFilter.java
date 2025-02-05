package de.servicehealth.epa4all.server.rest.filter;

import de.servicehealth.epa4all.server.cdi.TelematikIdLiteral;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.resteasy.runtime.standalone.QuarkusResteasySecurityContext;
import io.quarkus.security.AuthenticationFailedException;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.resteasy.core.interception.jaxrs.PostMatchContainerRequestContext;
import org.jboss.resteasy.core.interception.jaxrs.PreMatchContainerRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSession;
import java.lang.reflect.Field;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.stream.Stream;

@SuppressWarnings("rawtypes")
@Provider
@ApplicationScoped
@IfBuildProperty(name = "quarkus.ssl.native", stringValue = "true")
public class ClientCertificateFilter implements ContainerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ClientCertificateFilter.class.getName());

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (requestContext instanceof PostMatchContainerRequestContext postContext) {
            AuthenticationFailedException unauthorized = new AuthenticationFailedException("Unauthorized");
            try {
                Field contextDataMap = PreMatchContainerRequestContext.class.getDeclaredField("contextDataMap");
                contextDataMap.setAccessible(true);
                Map map = (Map) contextDataMap.get(postContext);
                QuarkusResteasySecurityContext context = (QuarkusResteasySecurityContext) map.get(jakarta.ws.rs.core.SecurityContext.class);

                Field request = QuarkusResteasySecurityContext.class.getDeclaredField("request");
                request.setAccessible(true);
                HttpServerRequest httpRequest = (HttpServerRequest) request.get(context);

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
