package de.servicehealth.logging;

import com.google.common.io.Files;
import io.quarkus.logging.LoggingFilter;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
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

    private static final Config CONFIG = ConfigProvider.getConfig();

    private static final String SHARE_PERSONAL_DATA = "servicehealth.client.share-personal-data";
    private static final boolean sharePersonalData = CONFIG.getOptionalValue(SHARE_PERSONAL_DATA, Boolean.class).orElse(false);

    private static final String PERSONAL_DATA_PATH = "servicehealth.client.personal-data.file.path";
    private static final ScheduledExecutorService SCHEDULED_EXECUTOR = Executors.newScheduledThreadPool(1);

    private static volatile Set<String> PERSONAL_DATA_KEYS = new HashSet<>();

    private static final Set<String> DEFAULT_PERSONAL_DATA_KEYS = new HashSet<>(Arrays.asList(
        """
            VAU-NP
            x-insurantid
            x-useragent
            x-workplace
            ClientID
            nonce
            vau-np
            authorizationCode
            clientAttest
            Base64Data
            Base64Signature
            MandantId
            ClientSystemId
            UserId
            Iccsn
            PersoenlicheVersichertendaten
            AllgemeineVersicherungsdaten
            GeschuetzteVersichertendaten
            Pruefungsnachweis
            GetCardsResponse
            CardHolderName
            CertificateExpirationDate
            CardHandle
            InsertTime
            Kyber768_ct
            AEAD_ct
            ECDH_ct
            AEAD_ct_key_confirmation
            Location
            ReadCardCertificate
            ReadCardCertificateResponse
            ExternalAuthenticate
            ExternalAuthenticateResponse
            ReadVSD
            Medikationsliste
            """.split("\n")
    ));

    static {
        SCHEDULED_EXECUTOR.scheduleWithFixedDelay(PublicLogFilter::reload, 0, 10, TimeUnit.MINUTES);
    }

    private static void reload() {
        Set<String> set = new HashSet<>();
        String path = CONFIG.getOptionalValue(PERSONAL_DATA_PATH, String.class).orElse("secret/personal-data.dict");
        try {
            File file = new File(path);
            set.addAll(Files.readLines(file, UTF_8).stream()
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet()));
        } catch (Throwable ignored) {
            set.addAll(DEFAULT_PERSONAL_DATA_KEYS);
        }
        if (!PERSONAL_DATA_KEYS.equals(set)) {
            String flag = sharePersonalData ? "ALLOWED" : "FORBIDDEN";
            log.info("Reloading personal data from '{}' after change, '{}' is {}", path, SHARE_PERSONAL_DATA, flag);
            PERSONAL_DATA_KEYS = set;
        }
    }

    @Override
    public boolean isLoggable(LogRecord record) {
        boolean nonPersonalData = PERSONAL_DATA_KEYS.parallelStream().noneMatch(record.getMessage()::contains);
        return sharePersonalData || nonPersonalData;
    }
}
