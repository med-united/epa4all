package de.servicehealth.epa4all.integration.base;

import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import io.quarkus.test.junit.QuarkusMock;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("UnusedReturnValue")
public class AbstractWebdavIT {

    protected static WebdavConfig mockWebdavConfig(File tempDir) {
        WebdavConfig webdavConfig = mock(WebdavConfig.class);
        when(webdavConfig.getRootFolder()).thenReturn(tempDir.getAbsolutePath());
        when(webdavConfig.getDefaultLimit()).thenReturn(20);
        when(webdavConfig.getAvailableProps(eq(true))).thenReturn(Map.of(
            "Mandatory", Arrays.asList("creationdate,getlastmodified,displayname,resourcetype".split(",")),
            "Root", Arrays.asList("".split("root")),
            "Telematik", Arrays.asList("smcb".split(",")),
            "Insurant", Arrays.asList("firstname,lastname,birthday".split(",")),
            "Category", Arrays.asList("firstname,lastname,birthday,entryuuid".split(","))
        ));
        when(webdavConfig.getAvailableProps(eq(false))).thenReturn(Map.of(
            "Mandatory", Arrays.asList("creationdate,getlastmodified,displayname".split(",")),
            "Checksum", Arrays.asList("entries".split(",")),
            "Entitlement", Arrays.asList("firstname,lastname,birthday,validto".split(",")),
            "Other", Arrays.asList("firstname,lastname,birthday,getcontenttype,getcontentlength".split(","))
        ));
        when(webdavConfig.getSmcbFolders()).thenReturn(
            Set.of(
                "eab_2ed345b1-35a3-49e1-a4af-d71ca4f23e57",
                "other_605a9f3c-bfe8-4830-a3e3-25a4ec6612cb",
                "local_00000000-0000-0000-0000-000000000000"
            )
        );

        QuarkusMock.installMockForType(new FolderService(webdavConfig), FolderService.class);
        QuarkusMock.installMockForType(webdavConfig, WebdavConfig.class);
        return webdavConfig;
    }
}
