package de.servicehealth.epa4all.server.rest.fileserver.prop;

import de.servicehealth.epa4all.server.rest.fileserver.paging.SortBy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.UriBuilder;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.PropFind;

import java.io.File;
import java.net.URI;
import java.util.List;

@ApplicationScoped
public class FileProp extends AbstractProp {

    @Override
    public MultiStatus propfind(
        UriBuilder uriBuilder,
        PropFind propFind,
        URI requestUri,
        File resource,
        int initialDepth,
        int currentDepth,
        SortBy sortBy
    ) throws Exception {
        return buildDavResponseStatus(resource, requestUri, propFind, false, sortBy);
    }

    @Override
    public List<String> resolveLevelProps(File resource, URI requestUri) {
        return propBuilder.resolveFileProps(resource);
    }
}
