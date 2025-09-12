package de.servicehealth.epa4all.server.rest.fileserver;

import jakarta.enterprise.context.Dependent;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Providers;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.PropFind;
import org.jugs.webdav.jaxrs.xml.elements.Rfc1123DateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static de.servicehealth.epa4all.server.rest.fileserver.paging.SortBy.Earliest;
import static de.servicehealth.folder.WebdavConfig.RESOURCE_NAME;
import static de.servicehealth.utils.MimeHelper.resolveMimeType;
import static java.io.File.separator;

@Dependent
public class FileResource extends AbstractResource {

    private static final Logger log = LoggerFactory.getLogger(FileResource.class.getName());

    private String davFolder;

    public void init(String davFolder, File resource, String url) {
        super.init(davFolder, resource, url);
        this.davFolder = davFolder;
    }

    @GET
    public Response get() {
        webdavMXBean.countRequest();
        logRequest("GET", url);
        if (!resource.exists()) {
            return Response.status(404).build();
        } else {
            Response.ResponseBuilder builder = Response.ok();
            InputStream in;
            try {
                in = new BufferedInputStream(new FileInputStream(resource));
            } catch (FileNotFoundException e) {
                return Response.serverError().build();
            }
            builder.header("Last-Modified", new Rfc1123DateFormat().format(new Date(resource.lastModified())));
            builder.header("Content-Length", resource.length());
            builder.type(resolveMimeType(resource.getName()));
            return logResponse("GET", builder.entity(in).build());
        }
    }

    @Override
    public Response delete() {
        webdavMXBean.countRequest();
        logRequest("DELETE", url);
        boolean deleted = resource.delete();
        if (deleted) {
            return logResponse("DELETE", Response.noContent().build());
        }
        return super.delete();
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
                if (destFile.delete()) {
                    log.info(String.format("%s was deleted.", destFile));
                }
                boolean moved = resource.renameTo(destFile);
                if (moved) {
                    return Response.created(new URI(originalDestination)).build();
                } else {
                    return Response.serverError().build();
                }
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
        PropFind propFind = getPropFind(contentLength, providers, httpHeaders, entityStream);
        URI requestUri = uriInfo.getRequestUri();
        UriBuilder uriBuilder = uriInfo.getRequestUriBuilder();
        int initialDepth = resolveDepth(depthValue);
        MultiStatus multiStatus = fileProp.propfind(
            uriBuilder, propFind, requestUri, resource, initialDepth, initialDepth, Earliest
        );
        return logResponse("PROPFIND", uriInfo, Response.ok(multiStatus).build());
    }

    @Override
    public Response put(final UriInfo uriInfo, final InputStream entityStream, final long contentLength) throws IOException {
        webdavMXBean.countRequest();
        return putFileOrUnknown(uriInfo, entityStream, contentLength);
    }

    @Override
    public Response options() {
        webdavMXBean.countRequest();
        log.debug("File - options(..)");
        Response.ResponseBuilder builder = withDavHeader(Response.ok());// noContent();
        /*
         * builder.header("Allow","");
         * OPTIONS, GET, HEAD, DELETE, PROPPATCH, COPY, MOVE, LOCK, UNLOCK, PROPFIND, PUT
         */
        builder.header("Allow", "OPTIONS,GET,MOVE,PUT,PROPPATCH,PROPFIND");
        return logResponse("OPTIONS", builder.build());
    }
}
