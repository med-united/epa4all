package de.servicehealth.epa4all.server.rest.fileserver;

import de.servicehealth.epa4all.server.config.WebdavConfig;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Providers;
import org.jugs.webdav.jaxrs.xml.elements.HRef;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.Prop;
import org.jugs.webdav.jaxrs.xml.elements.PropStat;
import org.jugs.webdav.jaxrs.xml.elements.Rfc1123DateFormat;
import org.jugs.webdav.jaxrs.xml.elements.Status;
import org.jugs.webdav.jaxrs.xml.properties.CreationDate;
import org.jugs.webdav.jaxrs.xml.properties.GetContentLength;
import org.jugs.webdav.jaxrs.xml.properties.GetContentType;
import org.jugs.webdav.jaxrs.xml.properties.GetLastModified;

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
import java.util.logging.Logger;

public class FileResource extends AbstractResource {

    private static final Logger log = Logger.getLogger(FileResource.class.getName());

    private final String davFolder;

    public FileResource(String davFolder, File resource, String url) {
        super(davFolder, resource, url);
        this.davFolder = davFolder;
    }

    @GET
    @Produces("application/octet-stream")
    public Response get() {
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

            return logResponse("GET", builder.entity(in).build());
        }
    }

    @Override
    public Response delete() {
        logRequest("DELETE", url);
        boolean deleted = resource.delete();
        if (deleted) {
            return logResponse("DELETE", Response.noContent().build());
        }
        return super.delete();
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
    public Response propfind(final UriInfo uriInfo, final int depth, final InputStream entityStream, final long contentLength, final Providers providers, final HttpHeaders httpHeaders) {
        logRequest("PROPFIND", uriInfo);

        Date lastModified = new Date(resource.lastModified());
        org.jugs.webdav.jaxrs.xml.elements.Response davFile = new org.jugs.webdav.jaxrs.xml.elements.Response(new HRef(uriInfo.getRequestUri()), null, null, null, new PropStat(new Prop(
            new CreationDate(lastModified), new GetLastModified(lastModified),
            new GetContentType("application/octet-stream"), new GetContentLength(resource.length())),
            new Status(Response.Status.OK)));

        MultiStatus st = new MultiStatus(davFile);

        return logResponse("PROPFIND", uriInfo, Response.ok(st).build());
    }


    @Override
    public Response put(final UriInfo uriInfo, final InputStream entityStream, final long contentLength) throws IOException {
        return putFileOrUnknown(uriInfo, entityStream, contentLength);
    }

    @Override
    public Response options() {
        log.fine("File - options(..)");
        Response.ResponseBuilder builder = withDavHeader(Response.ok());// noContent();
        /*
         * builder.header("Allow","");
         * OPTIONS, GET, HEAD, DELETE, PROPPATCH, COPY, MOVE, LOCK, UNLOCK, PROPFIND, PUT
         */
        builder.header("Allow", "OPTIONS,GET,MOVE,PUT,PROPPATCH,PROPFIND");
        return logResponse("OPTIONS", builder.build());
    }
}
