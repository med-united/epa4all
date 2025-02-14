package de.servicehealth.epa4all.server.rest.fileserver.prop;

import de.servicehealth.epa4all.server.rest.fileserver.paging.SortBy;
import de.servicehealth.epa4all.server.rest.fileserver.prop.type.DirectoryType;
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
import java.util.Map;

import static de.servicehealth.epa4all.server.filetracker.ChecksumFile.CHECKSUM_FILE_NAME;
import static java.util.stream.Collectors.toMap;

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

    @Override
    public List<String> resolveLevelProps(File resource, URI requestUri) {
        Map<String, List<String>> availableProps = webdavConfig.getAvailableProps(true);
        Map<DirectoryType, List<String>> directoryTypeMap = availableProps.entrySet().stream().collect(toMap(
            e -> DirectoryType.valueOf(e.getKey()), e -> e.getValue().stream().filter(s -> !s.isEmpty()).toList()
        ));
        List<String> props = new ArrayList<>(directoryTypeMap.get(DirectoryType.Mandatory));
        directoryTypeMap.entrySet().stream()
            .filter(e -> e.getKey().getLevel() == getLevel(requestUri))
            .findFirst()
            .map(e -> e.getValue().stream().filter(s -> !s.isEmpty()).toList()).ifPresent(props::addAll);
        return props;
    }

    private int getLevel(URI requestUri) {
        return getPathParts(requestUri).size() - 1;
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
                if (!file.getName().equals(CHECKSUM_FILE_NAME)) {
                    multiStatus = fileProp.propfind(nestedBuilder, propFind, nestedBuilder.build(), file, initialDepth, -1, sortBy);
                }
            }
            if (multiStatus != null) {
                nestedResources.addAll(multiStatus.getResponses());
            }
        }
    }
}