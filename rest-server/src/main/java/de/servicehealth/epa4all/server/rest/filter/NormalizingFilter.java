package de.servicehealth.epa4all.server.rest.filter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

@Provider
@PreMatching
@ApplicationScoped
public class NormalizingFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        UriInfo uriInfo = requestContext.getUriInfo();
        String originalPath = uriInfo.getPath();
        String normalizedPath = originalPath.replaceAll("//+", "/");
        if (!originalPath.equals(normalizedPath)) {
            requestContext.setRequestUri(
                uriInfo.getBaseUri(),
                uriInfo.getBaseUriBuilder().path(normalizedPath).build()
            );
        }
    }
}
