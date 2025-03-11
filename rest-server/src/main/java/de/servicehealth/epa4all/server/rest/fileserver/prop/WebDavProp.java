package de.servicehealth.epa4all.server.rest.fileserver.prop;

import de.servicehealth.epa4all.server.rest.fileserver.paging.SortBy;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.PropFind;

import java.io.File;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;

public interface WebDavProp {

    MultiStatus propfind(
        UriBuilder uriBuilder,
        PropFind propFind,
        URI requestUri,
        File resource,
        int initialDepth,
        int currentDepth,
        SortBy sortBy
    ) throws Exception;

    default Response.StatusType okStatus() {
        return Response.Status.OK;
    }

    default Response.StatusType notFound() {
        return Response.Status.NOT_FOUND;
    }
}