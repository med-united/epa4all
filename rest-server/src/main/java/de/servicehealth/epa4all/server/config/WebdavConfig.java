package de.servicehealth.epa4all.server.config;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Getter
@ApplicationScoped
public class WebdavConfig {

    public final static String RESOURCE_NAME = "webdav";

    @ConfigProperty(name = "webdav.root.folder")
    @Setter
    String rootFolder;

    @ConfigProperty(name = "webdav.prop.directory")
    String directoryProps;

    @ConfigProperty(name = "webdav.prop.file")
    String fileProps;

    @ConfigProperty(name = "smcb.folder")
    Set<String> smcbFolders;

    public List<String> getDirectoryProps() {
        if (directoryProps == null || directoryProps.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(directoryProps.split(","));
    }

    public List<String> getFileProps() {
        if (fileProps == null || fileProps.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(fileProps.split(","));
    }
}
