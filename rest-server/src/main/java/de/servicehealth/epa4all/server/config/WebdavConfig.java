package de.servicehealth.epa4all.server.config;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Set;

@Getter
@ApplicationScoped
public class WebdavConfig {

    public final static String RESOURCE_NAME = "webdav";

    @ConfigProperty(name = "webdav.root.folder")
    String rootFolder;

    @ConfigProperty(name = "smcb.folder")
    Set<String> smcbFolders;
}
