package de.servicehealth.epa4all.server.insurance;

import de.servicehealth.epa4all.server.filetracker.FileEvent;
import de.servicehealth.epa4all.server.filetracker.FileEventSender;
import de.servicehealth.epa4all.server.filetracker.FolderService;
import de.servicehealth.folder.WebdavConfig;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static de.servicehealth.epa4all.server.filetracker.FileOp.Delete;
import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;
import static java.util.concurrent.TimeUnit.HOURS;
import static org.apache.commons.io.FileUtils.deleteDirectory;

@ApplicationScoped
public class PatientDataJob {

    private static final Logger log = LoggerFactory.getLogger(PatientDataJob.class.getName());

    @Inject
    WebdavConfig webdavConfig;

    @Inject
    FolderService folderService;

    @Inject
    FileEventSender fileEventSender;

    @Inject
    InsuranceDataService insuranceDataService;

    @Scheduled(
        every = "${webdav.patient.data.job.interval:1h}",
        delay = 1,
        delayUnit = HOURS,
        concurrentExecution = SKIP
    )
    public void expirationMaintenance() {
        try {
            log.info("Patient data job started");
            Duration additionalRetainPeriod = webdavConfig.getAdditionalRetainPeriod();
            Arrays.stream(folderService.getTelematikFolders()).forEach(telematikFolder ->
                Arrays.stream(folderService.getNestedFolders(telematikFolder)).forEach(kvnrFolder ->
                    processKvnr(telematikFolder, kvnrFolder, additionalRetainPeriod)
                ));
        } catch (Throwable t) {
            log.error("Error while patient data expirationMaintenance", t);
        }
    }

    private void processKvnr(File telematikFolder, File kvnrFolder, Duration additionalRetainPeriod) {
        if (kvnrFolder.exists() && kvnrFolder.isDirectory()) {
            String telematikId = telematikFolder.getName();
            String kvnr = kvnrFolder.getName();
            Instant expiry = insuranceDataService.getEntitlementExpiry(telematikId, kvnr);
            boolean expired = expiry != null && expiry.plusSeconds(additionalRetainPeriod.toSeconds()).isBefore(Instant.now());
            if (expired) {
                try {
                    deleteDirectory(kvnrFolder);
                    fileEventSender.sendAsync(new FileEvent(Delete, telematikId, List.of(kvnrFolder)));
                    log.info("[%s] Patient data was successfully deleted, expiry = %s, additionalRetainPeriod = %s".formatted(
                        kvnr, expiry, additionalRetainPeriod)
                    );
                } catch (Exception e) {
                    log.error("[%s] Error while deleting expired patient data, expiry = %s".formatted(kvnr, expiry), e);
                }
            }
        }
    }
}
