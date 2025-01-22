package de.servicehealth.epa4all.server.rest.fileserver;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.PropFind;

import java.io.File;
import java.net.URI;

public interface WebDavProp {

    MultiStatus propfind(File resource, PropFind propFind, URI requestUri, UriBuilder uriBuilder, int depth) throws Exception;

    default Response.StatusType okStatus() {
        return Response.Status.OK;
    }

    default Response.StatusType notFound() {
        return Response.Status.NOT_FOUND;
    }
}
