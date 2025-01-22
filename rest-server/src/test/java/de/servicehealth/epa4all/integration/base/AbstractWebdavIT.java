package de.servicehealth.epa4all.integration.base;

import de.servicehealth.epa4all.server.config.WebdavConfig;
import io.quarkus.test.junit.QuarkusMock;

import java.io.File;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("UnusedReturnValue")
public class AbstractWebdavIT {

    protected WebdavConfig mockWebdavConfig(File tempDir) {
        WebdavConfig webdavConfig = mock(WebdavConfig.class);
        when(webdavConfig.getRootFolder()).thenReturn(tempDir.getAbsolutePath());
        when(webdavConfig.getSmcbFolders()).thenReturn(
            Set.of(
                "eab_2ed345b1-35a3-49e1-a4af-d71ca4f23e57",
                "other_605a9f3c-bfe8-4830-a3e3-25a4ec6612cb",
                "local_00000000-0000-0000-0000-000000000000"
            )
        );
        QuarkusMock.installMockForType(webdavConfig, WebdavConfig.class);
        return webdavConfig;
    }
}
