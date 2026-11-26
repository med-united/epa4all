package de.servicehealth.epa4all.unit;

import de.servicehealth.epa4all.server.logging.LogCleanupJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogCleanupJobTest {

    @TempDir
    Path logDir;

    @Test
    void deletesOnlyRotatedLogFilesOlderThanRetention() throws Exception {
        File active = logFile("quarkus.log", 40);
        File oldRotated = logFile("quarkus.log2026-01-01", 31);
        File oldBackup = logFile("quarkus.log2026-01-01.1", 31);
        File recentRotated = logFile("quarkus.log2026-02-01", 29);
        File unrelated = logFile("other.txt", 40);

        new LogCleanupJob(active.getPath(), 30).deleteOldLogs();

        assertFalse(oldRotated.exists());
        assertFalse(oldBackup.exists());
        assertTrue(active.exists());
        assertTrue(recentRotated.exists());
        assertTrue(unrelated.exists());
    }

    @Test
    void missingLogDirectoryIsIgnored() {
        new LogCleanupJob(logDir.resolve("missing/quarkus.log").toString(), 30).deleteOldLogs();
    }

    private File logFile(String name, int ageDays) throws Exception {
        File file = Files.createFile(logDir.resolve(name)).toFile();
        assertTrue(file.setLastModified(System.currentTimeMillis() - DAYS.toMillis(ageDays)));
        return file;
    }
}
