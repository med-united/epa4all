package de.servicehealth.epa4all.server.rest.fileserver;

import de.servicehealth.epa4all.server.jmx.WebdavMXBeanImpl;
import de.servicehealth.epa4all.server.rest.fileserver.paging.Paginator;
import de.servicehealth.epa4all.server.rest.fileserver.prop.DirectoryProp;
import de.servicehealth.epa4all.server.rest.fileserver.prop.FileProp;
import de.servicehealth.folder.WebdavConfig;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.Providers;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jugs.webdav.jaxrs.xml.elements.ActiveLock;
import org.jugs.webdav.jaxrs.xml.elements.Depth;
import org.jugs.webdav.jaxrs.xml.elements.HRef;
import org.jugs.webdav.jaxrs.xml.elements.LockRoot;
import org.jugs.webdav.jaxrs.xml.elements.LockScope;
import org.jugs.webdav.jaxrs.xml.elements.LockToken;
import org.jugs.webdav.jaxrs.xml.elements.LockType;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.Owner;
import org.jugs.webdav.jaxrs.xml.elements.Prop;
import org.jugs.webdav.jaxrs.xml.elements.PropFind;
import org.jugs.webdav.jaxrs.xml.elements.TimeOut;
import org.jugs.webdav.jaxrs.xml.properties.LockDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;

import static de.servicehealth.epa4all.server.rest.fileserver.paging.Paginator.X_TOTAL_COUNT;
import static de.servicehealth.utils.ServerUtils.writeStreamToFile;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML_TYPE;
import static org.jugs.webdav.jaxrs.Headers.DAV;

public class AbstractResource implements WebDavResource {

    private static final Logger log = LoggerFactory.getLogger(AbstractResource.class.getName());

    @Inject
    Instance<DirectoryResource> directoryResources;

    @Inject
    Instance<UnknownResource> unknownResources;

    @Inject
    Instance<FileResource> fileResources;

    @Inject
    DirectoryProp directoryProp;

    @Inject
    FileProp fileProp;

    @Inject
    WebdavConfig webdavConfig;

    @Inject
    protected WebdavMXBeanImpl webdavMXBean;

    protected String url;
    protected File resource;
    protected String rootFolder;

    public AbstractResource() {
    }

    protected void init(String rootFolder, File resource, String url) {
        this.rootFolder = rootFolder;
        this.resource = resource;
        this.url = url;
    }

    @Override
    public Response get(UriInfo uriInfo) {
        webdavMXBean.countRequest();
        logRequest("GET", uriInfo);
        Response.ResponseBuilder builder = Response.ok();
        builder.header(CONTENT_TYPE, MediaType.TEXT_HTML);
        try {
            if (!resource.exists()) {
                log.error(String.format("Resource '%s' does not exist (404).", resource));
                builder = Response.status(404);
                String html = MessageFormat.format(readResource("/static/404.html"), resource);
                builder.entity(html);
            } else if (resource.isDirectory()) {
                buildDirListing(uriInfo.getRequestUri(), builder);
            } else if (resource.isFile()) {
                builder = buildFileContent();
            } else {
                String html = MessageFormat.format(readResource("/static/index.html"), uriInfo.getRequestUri());
                builder.entity(html);
            }
        } catch (IOException ioe) {
            log.error("No resource found: -> " + ioe.getMessage());
            builder = Response.status(404);
        }
        return logResponse("GET", uriInfo, builder.build());
    }

    private void buildDirListing(URI uri, Response.ResponseBuilder builder) throws IOException {
        StringBuilder buf = new StringBuilder();
        File[] files = resource.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            buf.append(MessageFormat.format("<li><a href={0}/{1}>{1}</a></li>", uri, f.getName()));
        }
        buf.append(MessageFormat.format("<li><a href={0}/..>..</a></li>", uri));
        String html = MessageFormat.format(readResource("/static/dir.html"),
            resource.getPath(),
            buf,
            uri);
        builder.entity(html);
    }

    private Response.ResponseBuilder buildFileContent() throws IOException {
        Response.ResponseBuilder builder = Response.ok();
        builder.header(CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM);
        byte[] content = FileUtils.readFileToByteArray(resource);
        builder.entity(content);
        return builder;
    }

    protected static String readResource(String name) throws IOException {
        try (InputStream istream = AbstractResource.class.getResourceAsStream(name)) {
            if (istream == null) {
                throw new FileNotFoundException(String.format("resource '%s' not found", name));
            }
            return IOUtils.toString(istream, StandardCharsets.UTF_8);
        }
    }

    @Override
    public Response put(UriInfo uriInfo, final InputStream entityStream, final long contentLength) throws IOException {
        webdavMXBean.countRequest();
        logRequest("PUT", uriInfo);
        return logResponse("PUT", uriInfo, Response.status(501).build());
    }

    protected Response putFileOrUnknown(UriInfo uriInfo, final InputStream entityStream, final long contentLength) throws IOException {
        logRequest("PUT", uriInfo);

        /*
         * Workaround for Jersey issue #154 (see
         * https://jersey.dev.java.net/issues/show_bug.cgi?id=154): Jersey will
         * throw an exception and abstain from calling a method if the method
         * expects a JAXB element body while the actual Content-Length is zero.
         */

        if (contentLength == 0) {
            return Response.ok().build();
        }
        writeStreamToFile(entityStream, resource);

        log.debug(String.format("STORED: %s", resource.getName()));
        return logResponse("PUT", uriInfo, Response.created(uriInfo.getRequestUriBuilder().path(url).build()).build());
    }

    @Override
    public Response mkcol() {
        webdavMXBean.countRequest();
        log.debug("Abstract - mkcol(..)");
        return logResponse("MKCOL", Response.status(404).build());
    }

    @Override
    public Response propfind(
        UriInfo uriInfo,
        String depthValue,
        Long contentLength,
        Providers providers,
        HttpHeaders httpHeaders,
        InputStream entityStream
    ) throws Exception {
        logRequest("PROPFIND", uriInfo);
        return logResponse("PROPFIND", uriInfo, Response.status(404).build());
    }

    protected PropFind getPropFind(
        Long contentLength,
        Providers providers,
        HttpHeaders httpHeaders,
        InputStream entityStream
    ) {
        if (contentLength == null || contentLength == 0) {
            return null;
        }
        try {
            MessageBodyReader<PropFind> reader = providers.getMessageBodyReader(
                PropFind.class, PropFind.class, new Annotation[0], APPLICATION_XML_TYPE
            );
            return reader.readFrom(
                PropFind.class,
                PropFind.class,
                new Annotation[0],
                APPLICATION_XML_TYPE,
                httpHeaders.getRequestHeaders(),
                entityStream
            );
        } catch (Exception e) {
            return null;
        }
    }

    protected Response getDirectoryPropfindResponse(
        final UriInfo uriInfo,
        final String depthValue,
        final Long contentLength,
        final Providers providers,
        final HttpHeaders httpHeaders,
        final InputStream entityStream
    ) throws Exception {
        PropFind propFind = getPropFind(contentLength, providers, httpHeaders, entityStream);
        URI requestUri = uriInfo.getRequestUri();
        UriBuilder uriBuilder = uriInfo.getRequestUriBuilder();
        int initialDepth = resolveDepth(depthValue);
        Paginator paginator = new Paginator(webdavConfig, httpHeaders.getRequestHeaders());
        MultiStatus multiStatus = directoryProp.propfind(
            uriBuilder, propFind, requestUri, resource, initialDepth, initialDepth, paginator.getSortBy()
        );
        paginator.setTotalCount(multiStatus.getResponses().size());
        multiStatus = applyPagination(multiStatus, paginator);

        Response response;
        if (multiStatus.getResponses().isEmpty()) {
            response = Response.noContent().build();
        } else {
            response = Response.ok(multiStatus)
                .header(X_TOTAL_COUNT, paginator.getTotalCount())
                .build();
        }
        return logResponse("PROPFIND", uriInfo, response);
    }

    private MultiStatus applyPagination(MultiStatus multiStatus, Paginator pg) {
        return new MultiStatus(multiStatus.getResponses().stream().sorted((r1, r2) ->
            switch (pg.getSortBy()) {
                case Latest -> -1 * Long.valueOf(r1.getResponseDescription().getContent()).compareTo(Long.valueOf(r2.getResponseDescription().getContent()));
                case Earliest -> Long.valueOf(r1.getResponseDescription().getContent()).compareTo(Long.valueOf(r2.getResponseDescription().getContent()));
            }
        ).skip(pg.getOffset()).limit(pg.getLimit()).toArray(org.jugs.webdav.jaxrs.xml.elements.Response[]::new));
    }

    @Override
    public Response proppatch() {
        log.debug("Abstract - proppatch(..)");
        return logResponse("PROPPATCH", Response.status(404).build());
    }

    @Override
    public Response copy() {
        log.debug("Abstract - copy(..)");
        return logResponse("COPY", Response.status(404).build());
    }

    @Override
    public Response move(UriInfo uriInfo, String overwriteStr, String destination) throws URISyntaxException {
        logRequest("MOVE", uriInfo);
        return logResponse("MOVE", uriInfo, Response.status(404).build());
    }

    @Override
    public Response delete() {
        log.debug("Abstract - delete(..)");
        return logResponse("DELETE", Response.status(404).build());
    }

    @Override
    public Response options() {
        Response.ResponseBuilder builder = withDavHeader(Response.ok());// noContent();
        builder.header("Allow", "OPTIONS,GET,HEAD,POST,DELETE,PROPPATCH,PROPFIND,COPY,MOVE,PUT,MKCOL,LOCK,UNLOCK");
        return logResponse("OPTIONS", builder.build());
    }

    protected Response.ResponseBuilder withDavHeader(Response.ResponseBuilder builder) {
        builder.header(DAV, "1,2,3");
        builder.header("MS-Author-Via", "DAV");
        return builder;
    }

    @Override
    public Object findResource(final String res) {
        String path = resource.getPath() + File.separator + res;
        File newResource = new File(path);
        String newUrl = url + "/" + res;

        AbstractResource resource;
        if (newResource.exists()) {
            resource = newResource.isDirectory() ? directoryResources.get() : fileResources.get();
        } else {
            resource = unknownResources.get();
        }
        resource.init(rootFolder, newResource, newUrl);
        return resource;
    }

    @Override
    public Response lock(UriInfo uriInfo) {
        logRequest("LOCK", uriInfo);
        URI uri = uriInfo.getRequestUri();
        LockDiscovery lockDiscovery =
            new LockDiscovery(new ActiveLock(LockScope.SHARED, LockType.WRITE, Depth.ZERO, new Owner(""), new TimeOut(75), new LockToken(new HRef(
                uri)), new LockRoot(new HRef(uri))));
        Prop prop = new Prop(lockDiscovery);
        Response.ResponseBuilder builder = withDavHeader(Response.ok(prop));
        return logResponse("LOCK", uriInfo, builder.build());
    }

    @Override
    public Response unlock(UriInfo uriInfo, String token) {
        logRequest("UNLOCK", uriInfo);
        return logResponse("UNLOCK", uriInfo, withDavHeader(Response.noContent()).build());
    }

    protected static void logRequest(String method, UriInfo info) {
        log.debug(String.format("%s %s", method, info.getRequestUri()));
    }

    protected static void logRequest(String method, String context) {
        log.debug(String.format("%s %s", method, context));
    }

    protected Response logResponse(String method, Response resp) {
        log.info(String.format("%s %s: %s", method, url, resp.getStatus()));
        logHeaders(resp.getMetadata());
        return resp;
    }

    protected Response logResponse(String method, UriInfo ctx, Response resp) {
        log.info(String.format("%s %s: %s", method, ctx.getRequestUri(), resp.getStatus()));
        logHeaders(resp.getMetadata());
        return resp;
    }

    private static void logHeaders(MultivaluedMap<String, ?> headers) {
        log.debug("Headers:");
        for (String key : headers.keySet()) {
            log.debug(String.format("\t%s=%s", key, headers.get(key)));
        }
    }
}