package de.servicehealth.epa4all.server.rest.fileserver.prop;

import de.servicehealth.epa4all.server.rest.fileserver.WebDavProp;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.UriBuilder;
import org.jugs.webdav.jaxrs.xml.elements.HRef;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.Prop;
import org.jugs.webdav.jaxrs.xml.elements.PropFind;
import org.jugs.webdav.jaxrs.xml.elements.PropStat;
import org.jugs.webdav.jaxrs.xml.elements.Response;
import org.jugs.webdav.jaxrs.xml.elements.Status;
import org.jugs.webdav.jaxrs.xml.properties.CreationDate;
import org.jugs.webdav.jaxrs.xml.properties.DisplayName;
import org.jugs.webdav.jaxrs.xml.properties.GetContentLength;
import org.jugs.webdav.jaxrs.xml.properties.GetContentType;
import org.jugs.webdav.jaxrs.xml.properties.GetLastModified;

import java.io.File;
import java.net.URI;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;
import static jakarta.ws.rs.core.MediaType.TEXT_HTML;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static jakarta.ws.rs.core.MediaType.WILDCARD;

@ApplicationScoped
public class FileProp implements WebDavProp {

    public static final String APPLICATION_PDF = "application/pdf";

    private static final Map<String, Set<String>> MIME_MAP = Map.of(
        TEXT_PLAIN, Set.of(".txt", ".log"),
        TEXT_HTML, Set.of(".htm", ".html"),
        APPLICATION_XML, Set.of(".xml", ".xhtml"),
        APPLICATION_OCTET_STREAM, Set.of(".bin"),
        APPLICATION_JSON, Set.of(".json"),
        APPLICATION_PDF, Set.of(".pdf")
    );

    @Override
    public MultiStatus propfind(
        File resource,
        PropFind propFind,
        URI requestUri,
        UriBuilder uriBuilder,
        int depth
    ) throws Exception {
        Response davFile = new Response(
            new HRef(requestUri),
            null,
            null,
            null,
            buildFileStat(resource));

        return new MultiStatus(davFile);
    }

    private PropStat buildFileStat(File file) {
        Date lastModified = new Date(file.lastModified());
        Prop prop = new Prop(
            new CreationDate(lastModified),
            new GetLastModified(lastModified),
            new GetContentType(resolveMimeType(file.getName())),
            new GetContentLength(file.length()),
            new DisplayName(file.getName())
        );
        return new PropStat(prop, new Status(okStatus()));
    }

    public String resolveMimeType(String fileName) {
        return MIME_MAP.entrySet().stream()
            .filter(e -> e.getValue().stream().anyMatch(fileName::endsWith))
            .findFirst()
            .map(Map.Entry::getKey).orElse(WILDCARD);
    }
}
