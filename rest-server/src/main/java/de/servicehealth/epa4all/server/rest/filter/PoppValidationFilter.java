package de.servicehealth.epa4all.server.rest.filter;

import de.servicehealth.epa4all.server.popp.PoppConfig;
import io.quarkus.security.AuthenticationFailedException;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

import static de.servicehealth.epa4all.server.rest.filter.PoppUtils.extractInsurantId;
import static de.servicehealth.vau.VauClient.KVNR;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;

@Slf4j
@Provider
@ApplicationScoped
public class PoppValidationFilter implements ContainerRequestFilter {

    @Inject
    PoppConfig poppConfig;

    @Inject
    RoutingContext routingContext;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (poppConfig.isPoppAuthorizationFilterEnabled()) {
            try {
                HttpServerRequest httpRequest = routingContext.request();
                String insurantId = httpRequest.getParam(X_INSURANT_ID);
                if (insurantId == null) {
                    insurantId = httpRequest.getParam(KVNR);
                }
                if (insurantId == null) {
                    return;
                }
                String poppToken = extractPoppToken(requestContext);
                if (poppToken == null) {
                    throw new AuthenticationFailedException("PoPP token header not present");
                }
                if (!insurantId.equalsIgnoreCase(extractInsurantId(poppToken))) {
                    throw new AuthenticationFailedException("Unauthorized");
                }
            } catch (Exception e) {
                log.error("Error while client request popp validation", e);
                if (e instanceof AuthenticationFailedException unauthorizedException) {
                    throw unauthorizedException;
                } else {
                    throw new AuthenticationFailedException(e);
                }
            }
        }
    }

    private String extractPoppToken(ContainerRequestContext requestContext) {
        MultivaluedMap<String, String> headers = requestContext.getHeaders();
        List<String> poppValues = headers.entrySet().stream()
            .filter(e -> e.getKey().toLowerCase().contains("popp"))
            .findFirst()
            .map(Map.Entry::getValue)
            .orElse(List.of());

        return poppValues.isEmpty() ? null : poppValues.getFirst();
    }
}
