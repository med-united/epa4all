package de.servicehealth.epa4all.server.rest.fileserver;

import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.rest.fileserver.prop.DirectoryProp;
import de.servicehealth.epa4all.server.rest.fileserver.prop.FileProp;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
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
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

@Dependent
public class DirectoryResource extends AbstractResource {

    private static final Logger log = Logger.getLogger(DirectoryResource.class.getName());

    @Inject
    DirectoryProp directoryProp;

    private String davFolder;

    public void init(String davFolder, File resource, String url) {
        super.init(davFolder, resource, url);
        this.davFolder = davFolder;
    }

    @Override
    public Response move(final UriInfo uriInfo, String overwriteStr, String destination) throws URISyntaxException {
        logRequest("MOVE", uriInfo);
        URI uri = uriInfo.getBaseUri();
        String host = uri.getScheme() + "://" + uri.getHost() + "/" + WebdavConfig.RESOURCE_NAME + "/";
        String originalDestination = destination;
        destination = URLDecoder.decode(destination, StandardCharsets.UTF_8);
        destination = destination.replace(host, "");

        File destFile = new File(davFolder + File.separator + destination);
        boolean overwrite = overwriteStr.equalsIgnoreCase("T");

        return logResponse("MOVE", uriInfo, move(originalDestination, destFile, overwrite));
    }

    private Response move(String originalDestination, File destFile, boolean overwrite)
        throws URISyntaxException {
        if (destFile.equals(resource)) {
            return Response.status(403).build();
        } else {
            if (destFile.exists() && !overwrite) {
                return Response.status(Response.Status.PRECONDITION_FAILED).build();
            }
            if (!destFile.exists() || overwrite) {
                destFile.delete();
                boolean moved = resource.renameTo(destFile);
                if (moved)
                    return Response.created(new URI(originalDestination)).build();
                else
                    return Response.serverError().build();
            }
            return Response.status(409).build();
        }
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
        if (resource.exists()) {
            PropFind propFind = getPropFind(contentLength, providers, httpHeaders, entityStream);
            URI requestUri = uriInfo.getRequestUri();
            UriBuilder uriBuilder = uriInfo.getRequestUriBuilder();
            MultiStatus multiStatus = directoryProp.propfind(resource, propFind, requestUri, uriBuilder, resolveDepth(depth));
            return multiStatus.getResponses().isEmpty()
                ? logResponse("PROPFIND", uriInfo, Response.noContent().build())
                : logResponse("PROPFIND", uriInfo, Response.ok(multiStatus).build());
        } else {
            return logResponse("PROPFIND", uriInfo, Response.status(404).build());
        }
    }
}
