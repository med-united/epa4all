package de.servicehealth.epa4all.server.rest.fileserver;

import de.servicehealth.epa4all.server.config.WebdavConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Providers;
import org.jugs.webdav.jaxrs.xml.elements.HRef;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.Prop;
import org.jugs.webdav.jaxrs.xml.elements.PropStat;
import org.jugs.webdav.jaxrs.xml.elements.Status;
import org.jugs.webdav.jaxrs.xml.properties.CreationDate;
import org.jugs.webdav.jaxrs.xml.properties.DisplayName;
import org.jugs.webdav.jaxrs.xml.properties.GetContentLength;
import org.jugs.webdav.jaxrs.xml.properties.GetContentType;
import org.jugs.webdav.jaxrs.xml.properties.GetLastModified;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.jugs.webdav.jaxrs.xml.properties.ResourceType.COLLECTION;

@Path(WebdavConfig.RESOURCE_NAME)
public class FileServerResource extends AbstractResource {

    private static final Logger log = Logger.getLogger(FileServerResource.class.getName());

    public FileServerResource() {
    }

    @Inject
    public FileServerResource(WebdavConfig webdavConfig) {
        super(webdavConfig.getRootFolder(), new File(webdavConfig.getRootFolder()), "");
    }

    @Override
    public Response propfind(
        final UriInfo uriInfo,
        final int depth,
        final InputStream entityStream,
        final long contentLength,
        final Providers providers,
        final HttpHeaders httpHeaders
    ) {
        logRequest("PROPFIND", uriInfo);
        URI uri = uriInfo.getRequestUri();

        Date lastModified = new Date(resource.lastModified());
        CreationDate creationDate = new CreationDate(lastModified);
        GetLastModified getLastModified = new GetLastModified(lastModified);
        Prop prop = new Prop(creationDate, getLastModified, COLLECTION);
        PropStat propStat = new PropStat(prop, new Status(OK));
        
        final org.jugs.webdav.jaxrs.xml.elements.Response folder = new org.jugs.webdav.jaxrs.xml.elements.Response(
            new HRef(uri),
            null,
            null,
            null,
            propStat
        );
        return logResponse("PROPFIND", uriInfo, propfind(uriInfo, depth, folder));
    }

    private Response propfind(UriInfo uriInfo, int depth, org.jugs.webdav.jaxrs.xml.elements.Response folder) {
        Date lastModified;
        if (depth == 0) {
            return Response.ok(new MultiStatus(folder)).build();
        }

        File[] files = resource.listFiles();
        if (files == null) {
            return Response.status(NOT_FOUND).build();
        }
        List<org.jugs.webdav.jaxrs.xml.elements.Response> responses = new ArrayList<>();
        responses.add(folder);
        for (File file : files) {
            lastModified = new Date(file.lastModified());
            HRef hRef = new HRef(uriInfo.getRequestUriBuilder().path(file.getName()).build());
            PropStat propStat = getPropStat(file, lastModified);
            responses.add(new org.jugs.webdav.jaxrs.xml.elements.Response(
                hRef,
                null,
                null,
                null,
                propStat
            ));
        }
        MultiStatus st = new MultiStatus(
            responses.toArray(new org.jugs.webdav.jaxrs.xml.elements.Response[responses.size()])
        );
        return Response.ok(st).build();
    }

    private static PropStat getPropStat(File file, Date lastModified) {
        CreationDate creationDate = new CreationDate(lastModified);
        GetLastModified getLastModified = new GetLastModified(lastModified);
        DisplayName displayName = new DisplayName(file.getName());
        Status statusOk = new Status(OK);
        PropStat propStat;
        if (file.isDirectory()) {
            Prop prop = new Prop(creationDate, getLastModified, COLLECTION, displayName);
            propStat = new PropStat(prop, statusOk);
        } else {
            GetContentType contentType = new GetContentType("application/octet-stream");
            GetContentLength contentLength = new GetContentLength(file.length());
            Prop prop = new Prop(creationDate, getLastModified, contentType, contentLength, displayName);
            propStat = new PropStat(prop, statusOk);
        }
        return propStat;
    }
}