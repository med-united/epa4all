package de.servicehealth.epa4all.server.logging;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;
import static java.util.concurrent.TimeUnit.DAYS;

/**
 * The file log is rotated per day and by size, but Quarkus never deletes the files of past days,
 * so they would fill the disk over time.
 */
@ApplicationScoped
public class LogCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(LogCleanupJob.class.getName());

    private final File logFile;
    private final int retentionDays;

    @Inject
    public LogCleanupJob(
        @ConfigProperty(name = "quarkus.log.file.path", defaultValue = "quarkus.log") String logFilePath,
        @ConfigProperty(name = "log.retention.days", defaultValue = "30") int retentionDays
    ) {
        this.logFile = new File(logFilePath).getAbsoluteFile();
        this.retentionDays = retentionDays;
    }

    @Scheduled(every = "24h", concurrentExecution = SKIP)
    public void deleteOldLogs() {
        String activeName = logFile.getName();
        File[] rotatedFiles = logFile.getParentFile().listFiles(
            (dir, name) -> name.startsWith(activeName) && !name.equals(activeName)
        );
        if (rotatedFiles == null) {
            return;
        }
        long threshold = System.currentTimeMillis() - DAYS.toMillis(retentionDays);
        for (File file : rotatedFiles) {
            if (file.lastModified() < threshold && file.delete()) {
                log.info(String.format("Deleted log file %s, older than %d days", file.getName(), retentionDays));
            }
        }
    }
}
