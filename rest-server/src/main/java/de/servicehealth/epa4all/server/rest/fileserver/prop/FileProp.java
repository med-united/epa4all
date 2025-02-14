package de.servicehealth.epa4all.server.rest.fileserver.prop;

import de.servicehealth.epa4all.server.rest.fileserver.paging.SortBy;
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
        Map<String, List<String>> availableProps = webdavConfig.getAvailableProps(false);
        Map<FileType, List<String>> fileTypeMap = availableProps.entrySet().stream().collect(toMap(
            e -> FileType.valueOf(e.getKey()), e -> e.getValue().stream().filter(s -> !s.isEmpty()).toList()
        ));
        List<String> props = new ArrayList<>(fileTypeMap.get(FileType.Mandatory));
        props.addAll(fileTypeMap.get(FileType.fromName(resource.getName())));
        return props;
    }
}
