package de.servicehealth.folder;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@ApplicationScoped
public class WebdavConfig {

    public final static String RESOURCE_NAME = "webdav";

    @ConfigProperty(name = "webdav.root.folder")
    @Setter
    String rootFolder;

    @ConfigProperty(name = "webdav.paging.default.limit", defaultValue = "20")
    int defaultLimit;

    @ConfigProperty(name = "webdav.patient.data.additional.retain.period", defaultValue = "0d")
    Duration additionalRetainPeriod;

    @ConfigProperty(name = "webdav.prop.directory")
    Map<String, String> directoryPropsMap;

    @ConfigProperty(name = "webdav.prop.file")
    Map<String, String> filePropsMap;

    @ConfigProperty(name = "smcb.folder")
    Map<String, String> smcbFolders;

    public Map<String, List<String>> getAvailableProps(boolean directory) {
        Map<String, String> propsMap = directory ? getDirectoryPropsMap() : getFilePropsMap();
        return propsMap.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey, e -> Arrays.stream(e.getValue().split(",")).filter(s -> !s.isEmpty()).toList()
        ));
    }
}