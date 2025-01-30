package de.servicehealth.epa4all.server.rest.fileserver;

import jakarta.enterprise.context.Dependent;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jugs.webdav.jaxrs.methods.MKCOL;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

@Dependent
public class UnknownResource extends AbstractResource {

    private static final Logger log = Logger.getLogger(UnknownResource.class.getName());

    public void init(String davFolder, File resource, String url) {
        super.init(davFolder, resource, url);
    }

    @MKCOL
    public Response mkcol() {
        log.fine("mkcol(..folder..) - " + url);
        return logResponse("MKCOL", doMkcol());
    }

    private Response doMkcol() {
        if (resource.exists()) {
            return Response.status(405).build();
        } else {
            boolean created = resource.mkdirs();
            if (created) {
                return Response.status(201).build();
            } else {
                return Response.status(403).build();
            }
        }
    }

    @Override
    public Response put(final UriInfo uriInfo, final InputStream entityStream, final long contentLength) throws IOException {
        return putFileOrUnknown(uriInfo, entityStream, contentLength);
    }

    @Override
    public Response options() {
        log.fine("UnknownResource - options(..)");
        Response.ResponseBuilder builder = withDavHeader(Response.ok());// noContent();
        /*
         * builder.header("Allow","");
         * OPTIONS, GET, HEAD, DELETE, PROPPATCH, COPY, MOVE, LOCK, UNLOCK, PROPFIND, PUT
         */
        builder.header("Allow", "OPTIONS,MKCOL,PUT");
        return builder.build();
    }
}