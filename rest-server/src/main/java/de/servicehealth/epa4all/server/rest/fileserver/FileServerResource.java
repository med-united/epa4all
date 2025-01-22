package de.servicehealth.epa4all.server.rest.fileserver;

import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.rest.fileserver.prop.DirectoryProp;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Providers;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.PropFind;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.server.config.WebdavConfig.RESOURCE_NAME;

@SuppressWarnings("unused")
@Path(RESOURCE_NAME)
public class FileServerResource extends AbstractResource {

    private static final Logger log = Logger.getLogger(FileServerResource.class.getName());

    @Inject
    DirectoryProp directoryProp;

    public FileServerResource() {
    }

    @Inject
    public FileServerResource(WebdavConfig webdavConfig) {
        init(webdavConfig.getRootFolder(), new File(webdavConfig.getRootFolder()), "");
    }

    @Override
    public Response propfind(
        final UriInfo uriInfo,
        final String depth,
        final Long contentLength,
        final Providers providers,
        final HttpHeaders httpHeaders,
        final InputStream entityStream
    ) throws Exception {
        logRequest("PROPFIND", uriInfo);

        PropFind propFind = getPropFind(contentLength, providers, httpHeaders, entityStream);
        URI requestUri = uriInfo.getRequestUri();
        UriBuilder uriBuilder = uriInfo.getRequestUriBuilder();
        MultiStatus multiStatus = directoryProp.propfind(resource, propFind, requestUri, uriBuilder, resolveDepth(depth));
        return multiStatus.getResponses().isEmpty()
            ? logResponse("PROPFIND", uriInfo, Response.noContent().build())
            : logResponse("PROPFIND", uriInfo, Response.ok(multiStatus).build());
    }
}