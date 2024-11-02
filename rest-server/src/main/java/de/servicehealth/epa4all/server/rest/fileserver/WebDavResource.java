package de.servicehealth.epa4all.server.rest.fileserver;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Providers;
import org.jugs.webdav.jaxrs.methods.COPY;
import org.jugs.webdav.jaxrs.methods.LOCK;
import org.jugs.webdav.jaxrs.methods.MKCOL;
import org.jugs.webdav.jaxrs.methods.MOVE;
import org.jugs.webdav.jaxrs.methods.PROPFIND;
import org.jugs.webdav.jaxrs.methods.PROPPATCH;
import org.jugs.webdav.jaxrs.methods.UNLOCK;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static org.jugs.webdav.jaxrs.Headers.DEPTH;
import static org.jugs.webdav.jaxrs.Headers.DESTINATION;
import static org.jugs.webdav.jaxrs.Headers.OVERWRITE;

public interface WebDavResource {

    @GET
    @Produces("text/html")
    Response get(@Context final UriInfo uriInfo);

    @PUT
    @Consumes("application/octet-stream")
    Response put(
        @Context final UriInfo uriInfo,
        final InputStream entityStream,
        @HeaderParam(CONTENT_LENGTH) final long contentLength
    ) throws IOException, URISyntaxException;

    @POST
    @Consumes("application/octet-stream")
    default Response post(
        @Context final UriInfo uriInfo,
        final InputStream entityStream,
        @HeaderParam(CONTENT_LENGTH) final long contentLength
    ) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @MKCOL
    Response mkcol();

    @Produces("application/xml")
    @PROPFIND
    Response propfind(
        @Context final UriInfo uriInfo,
        @HeaderParam(DEPTH) final int depth,
        final InputStream entityStream,
        @HeaderParam(CONTENT_LENGTH) final long contentLength,
        @Context final Providers providers,
        @Context final HttpHeaders httpHeaders
    ) throws URISyntaxException, IOException;

    @PROPPATCH
    Response proppatch();

    @COPY
    Response copy();

    /*
        201 (Created)	The resource was moved successfully and a new resource was created at the specified destination URI.
        204 (No Content)	The resource was moved successfully to a pre-existing destination URI.
        403 (Forbidden)	The source URI and the destination URI are the same.
        409 (Conflict)	A resource cannot be created at the destination URI until one or more intermediate collections are created.
        412 (Precondition Failed)	Either the Overwrite header is "F" and the state of the destination resource is not null, or the method was used in a Depth: 0 transaction.
        423 (Locked)	The destination resource is locked.
        502 (Bad Gateway)	The destination URI is located on a different server, which refuses to accept the resource.
     */
    @MOVE
    Response move(
        @Context final UriInfo uriInfo,
        @HeaderParam(OVERWRITE) final String overwriteStr,
        @HeaderParam(DESTINATION) final String destination
    ) throws URISyntaxException;

    @DELETE
    Response delete();

    @Path("{resource}")
    Object findResource(@PathParam("resource") final String res);

    @LOCK
    Response lock(@Context final UriInfo uriInfo);

    @UNLOCK
    Response unlock(@Context final UriInfo uriInfo, String token);

    @jakarta.ws.rs.OPTIONS
    Response options();
}
