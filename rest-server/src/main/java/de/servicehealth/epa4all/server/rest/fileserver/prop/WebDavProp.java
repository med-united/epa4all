package de.servicehealth.epa4all.server.rest.fileserver.prop;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.PropFind;

import java.io.File;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;

public interface WebDavProp {

    DateTimeFormatter LOCALDATE_YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    DateTimeFormatter LOCALDATE_YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    SimpleDateFormat DATE_YYYY_MM_DD = new SimpleDateFormat("yyyy-MM-dd");

    MultiStatus propfind(
        File resource,
        PropFind propFind,
        URI requestUri,
        UriBuilder uriBuilder,
        int depth
    ) throws Exception;

    default Response.StatusType okStatus() {
        return Response.Status.OK;
    }

    default Response.StatusType notFound() {
        return Response.Status.NOT_FOUND;
    }
}