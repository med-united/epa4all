package de.servicehealth.epa4all.server.rest.fileserver;

import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.rest.fileserver.prop.DirectoryProp;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Providers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;

import static de.servicehealth.epa4all.server.config.WebdavConfig.RESOURCE_NAME;

@SuppressWarnings("unused")
@Path(RESOURCE_NAME)
public class FileServerResource extends AbstractResource {

    private static final Logger log = LoggerFactory.getLogger(FileServerResource.class.getName());

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
        return getDirectoryPropfindResponse(uriInfo, depth, contentLength, providers, httpHeaders, entityStream);
    }
}