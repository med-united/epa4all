package de.servicehealth.logging;

import com.google.common.io.Files;
import io.quarkus.logging.LoggingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

@LoggingFilter(name = "de.servicehealth.logging.PublicLogFilter")
public class PublicLogFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(PublicLogFilter.class.getName());

    private static final String PERSONAL_DATA_FILE = "personal-data.dict";
    private static final ScheduledExecutorService SCHEDULED_EXECUTOR = Executors.newScheduledThreadPool(1);

    private static volatile Set<String> PERSONAL_DATA_KEYS = new HashSet<>();

    private static final Set<String> DEFAULT_PERSONAL_DATA_KEYS = new HashSet<>(Arrays.asList(
        "MandantId",
        "ClientSystemId",
        "WorkplaceId",
        "UserId",
        "GetCards",
        "GetSubscription",
        "GetSubscriptionResponse",
        "SubscriptionID",
        "GetCardsResponse",
        "Iccsn",
        "CardHolderName",
        "CertificateExpirationDate",
        "CardHandle",
        "InsertTime",
        "Kyber768_ct",
        "AEAD_ct",
        "ECDH_ct",
        "AEAD_ct_key_confirmation",
        "nonce",
        "Location",
        "ReadCardCertificate",
        "ReadCardCertificateResponse",
        "ExternalAuthenticate",
        "ExternalAuthenticateResponse",
        "authorizationCode",
        "vau-np",
        "ReadVSD",
        "VAU-NP",
        "kvnr",
        "Medikationsliste",
        "x-insurantid",
        "fhir/Medication",
        "eml/xhtml"
    ));

    static {
        SCHEDULED_EXECUTOR.scheduleWithFixedDelay(PublicLogFilter::reload, 0, 10, TimeUnit.MINUTES);
    }

    private static void reload() {
        Set<String> set = new HashSet<>();
        File file = new File("secret/" + PERSONAL_DATA_FILE);
        String path = file.getAbsolutePath();
        try {
            set.addAll(Files.readLines(file, UTF_8).stream()
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet()));
        } catch (Throwable ignored) {
            set.addAll(DEFAULT_PERSONAL_DATA_KEYS);
        }
        if (!PERSONAL_DATA_KEYS.equals(set)) {
            log.info("Reloading personal data from '{}' after change", path);
            PERSONAL_DATA_KEYS = set;
        }
    }

    @Override
    public boolean isLoggable(LogRecord record) {
        return PERSONAL_DATA_KEYS.parallelStream().noneMatch(record.getMessage()::contains);
    }
}
