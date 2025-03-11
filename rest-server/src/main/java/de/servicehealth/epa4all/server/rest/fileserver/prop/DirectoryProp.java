package de.servicehealth.epa4all.server.rest.fileserver.prop;

import de.servicehealth.epa4all.server.rest.fileserver.paging.SortBy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.UriBuilder;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.PropFind;
import org.jugs.webdav.jaxrs.xml.elements.Response;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static de.servicehealth.epa4all.server.propsource.PropBuilder.SKIPPED_FILES;
import static de.servicehealth.utils.ServerUtils.getPathParts;

@ApplicationScoped
public class DirectoryProp extends AbstractProp {

    @Inject
    FileProp fileProp;

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
        MultiStatus multiStatus = buildDavResponseStatus(resource, requestUri, propFind, true, sortBy);
        if (currentDepth <= 0) {
            return multiStatus;
        } else {
            List<Response> nestedResources = currentDepth < initialDepth
                ? new ArrayList<>(multiStatus.getResponses())
                : new ArrayList<>();
            collectNestedResources(nestedResources, resource, uriBuilder, propFind, initialDepth, currentDepth - 1, sortBy);
            return new MultiStatus(nestedResources.toArray(Response[]::new));
        }
    }

    private int getLevel(URI requestUri) {
        return getPathParts(requestUri.getPath()).size() - 1;
    }

    @Override
    public List<String> resolveLevelProps(File resource, URI requestUri) {
        return propBuilder.resolveDirectoryProps(getLevel(requestUri));
    }

    private void collectNestedResources(
        List<Response> nestedResources,
        File resource,
        UriBuilder uriBuilder,
        PropFind propFind,
        int initialDepth,
        int currentDepth,
        SortBy sortBy
    ) throws Exception {
        File[] files = resource.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            UriBuilder nestedBuilder = uriBuilder.clone().path(file.getName());
            MultiStatus multiStatus = null;
            if (file.isDirectory()) {
                if (currentDepth >= 0) {
                    multiStatus = propfind(nestedBuilder, propFind, nestedBuilder.build(), file, initialDepth, currentDepth, sortBy);
                }
            } else {
                if (SKIPPED_FILES.test(file)) {
                    multiStatus = fileProp.propfind(nestedBuilder, propFind, nestedBuilder.build(), file, initialDepth, -1, sortBy);
                }
            }
            if (multiStatus != null) {
                nestedResources.addAll(multiStatus.getResponses());
            }
        }
    }
}