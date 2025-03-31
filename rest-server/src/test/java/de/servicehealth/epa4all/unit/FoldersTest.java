package de.servicehealth.epa4all.unit;

import de.servicehealth.epa4all.server.filetracker.FileEventSender;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.folder.WebdavConfig;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static de.servicehealth.epa4all.common.TestUtils.deleteFiles;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FoldersTest {

    @Test
    public void medFoldersAreNooCreatedWithoutInsurant() throws Exception {
        Path tempDir = Files.createTempDirectory(UUID.randomUUID().toString());
        try {
            File webdav = new File(tempDir.toFile(), "webdav");
            webdav.mkdirs();
            WebdavConfig webdavConfig = mock(WebdavConfig.class);
            when(webdavConfig.getRootFolder()).thenReturn(webdav.getAbsolutePath());
            when(webdavConfig.getSmcbFolders()).thenReturn(
                Map.of(
                    "eab", "2ed345b1-35a3-49e1-a4af-d71ca4f23e57",
                    "other", "605a9f3c-bfe8-4830-a3e3-25a4ec6612cb",
                    "local", "00000000-0000-0000-0000-000000000000"
                )
            );
            FileEventSender fileEventSender = mock(FileEventSender.class);
            FolderService folderService = new FolderService(webdavConfig, fileEventSender);
            File telematikFolder = folderService.initInsurantFolders("telematikId", " ");
            File[] files = telematikFolder.listFiles();
            assertNotNull(files);
            assertEquals(0, files.length);
        } finally {
            deleteFiles(tempDir.toFile().listFiles());
            tempDir.toFile().delete();
        }
    }
}