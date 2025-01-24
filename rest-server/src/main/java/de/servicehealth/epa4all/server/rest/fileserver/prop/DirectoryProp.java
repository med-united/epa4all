package de.servicehealth.epa4all.server.rest.fileserver.prop;

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

import static java.util.stream.Collectors.toMap;

@ApplicationScoped
public class DirectoryProp extends AbstractWebDavProp {

    @Inject
    FileProp fileProp;

    @Override
    public MultiStatus propfind(
        File resource,
        PropFind propFind,
        URI requestUri,
        UriBuilder uriBuilder,
        int depth
    ) throws Exception {
        MultiStatus multiStatus = buildDavResponseStatus(resource, requestUri, propFind, true);
        if (depth <= 0) {
            return multiStatus;
        } else {
            List<Response> nestedResources = new ArrayList<>(multiStatus.getResponses());
            collectNestedResources(nestedResources, resource, uriBuilder, propFind, depth - 1);
            return new MultiStatus(nestedResources.toArray(Response[]::new));
        }
    }

    @Override
    public List<String> resolveLevelProps(Map<String, List<String>> availableProps, File resource, URI requestUri) {
        Map<DirectoryType, List<String>> directoryTypeMap = availableProps.entrySet().stream().collect(toMap(
            e -> DirectoryType.valueOf(e.getKey()), Map.Entry::getValue
        ));
        List<String> props = new ArrayList<>(directoryTypeMap.get(DirectoryType.Mandatory));
        directoryTypeMap.entrySet().stream()
            .filter(e -> e.getKey().getLevel() == getLevel(requestUri))
            .findFirst()
            .map(e -> e.getValue().stream().filter(s -> !s.isEmpty()).toList()).ifPresent(props::addAll);
        return props;
    }

    private void collectNestedResources(
        List<Response> nestedResources,
        File resource,
        UriBuilder uriBuilder,
        PropFind propFind,
        int depth
    ) throws Exception {
        File[] files = resource.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            UriBuilder nestedBuilder = uriBuilder.clone().path(file.getName());
            MultiStatus multiStatus = null;
            if (file.isDirectory()) {
                if (depth >= 0) {
                    multiStatus = propfind(file, propFind, nestedBuilder.build(), nestedBuilder, depth);
                }
            } else {
                multiStatus = fileProp.propfind(file, propFind, nestedBuilder.build(), nestedBuilder, -1);
            }
            if (multiStatus != null) {
                nestedResources.addAll(multiStatus.getResponses());
            }
        }
    }
}