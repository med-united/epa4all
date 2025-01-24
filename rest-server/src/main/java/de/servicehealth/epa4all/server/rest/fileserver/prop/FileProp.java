package de.servicehealth.epa4all.server.rest.fileserver.prop;

import de.servicehealth.epa4all.server.rest.fileserver.prop.type.FileType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.UriBuilder;
import org.jugs.webdav.jaxrs.xml.elements.MultiStatus;
import org.jugs.webdav.jaxrs.xml.elements.PropFind;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

@ApplicationScoped
public class FileProp extends AbstractWebDavProp {

    @Override
    public MultiStatus propfind(
        File resource,
        PropFind propFind,
        URI requestUri,
        UriBuilder uriBuilder,
        int depth
    ) throws Exception {
        return buildDavResponseStatus(resource, requestUri, propFind, false);
    }

    @Override
    public List<String> resolveLevelProps(Map<String, List<String>> availableProps, File resource, URI requestUri) {
        Map<FileType, List<String>> fileTypeMap = availableProps.entrySet().stream().collect(toMap(
            e -> FileType.valueOf(e.getKey()), e -> e.getValue().stream().filter(s -> !s.isEmpty()).toList()
        ));
        List<String> props = new ArrayList<>(fileTypeMap.get(FileType.Mandatory));
        props.addAll(fileTypeMap.get(FileType.fromName(resource.getName())));
        return props;
    }
}
