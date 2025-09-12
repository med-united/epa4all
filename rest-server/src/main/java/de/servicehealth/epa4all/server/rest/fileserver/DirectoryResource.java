package de.servicehealth.epa4all.server.rest.fileserver;

import jakarta.enterprise.context.Dependent;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Providers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static de.servicehealth.folder.WebdavConfig.RESOURCE_NAME;
import static java.io.File.separator;

@Dependent
public class DirectoryResource extends AbstractResource {

    private static final Logger log = LoggerFactory.getLogger(DirectoryResource.class.getName());

    private String davFolder;

    public void init(String davFolder, File resource, String url) {
        super.init(davFolder, resource, url);
        this.davFolder = davFolder;
    }

    @Override
    public Response move(final UriInfo uriInfo, String overwriteStr, String destination) throws URISyntaxException {
        webdavMXBean.countRequest();
        logRequest("MOVE", uriInfo);
        URI uri = uriInfo.getBaseUri();
        String host = uri.getScheme() + "://" + uri.getHost() + "/" + RESOURCE_NAME + "/";
        String originalDestination = destination;
        destination = URLDecoder.decode(destination, StandardCharsets.UTF_8);
        destination = destination.replace(host, "");

        File destFile = new File(davFolder + separator + destination);
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
        final String depthValue,
        final Long contentLength,
        final Providers providers,
        final HttpHeaders httpHeaders,
        final InputStream entityStream
    ) throws Exception {
        webdavMXBean.countRequest();
        logRequest("PROPFIND", uriInfo);
        if (resource.exists()) {
            return getDirectoryPropfindResponse(uriInfo, depthValue, contentLength, providers, httpHeaders, entityStream);
        } else {
            return logResponse("PROPFIND", uriInfo, Response.status(404).build());
        }
    }
}
