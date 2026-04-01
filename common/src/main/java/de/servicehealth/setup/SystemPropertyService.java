package de.servicehealth.setup;

import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class SystemPropertyService {

    private static final Logger log = LoggerFactory.getLogger(SystemPropertyService.class.getName());

    private static final Set<String> PROD_PROFILES = Set.of("prod", "pu");

    private static final String ALL = "all";
    private static final String NONE = "none";
    private static final Set<String> OTHER_ACCESS_DTD_VALUES = Set.of("file", "http", "https", "ftp", "jar");

    public void onStart(@Observes @Priority(40) StartupEvent ev) {
        // Always allowed for JCR processing
        System.setProperty("disableCheckForReferencesInContentException", "true");

        String dtdAccess = ConfigProvider.getConfig().getValue("access.external.dtd", String.class).toLowerCase();
        setPropertyVerbose("javax.xml.accessExternalDTD", normalize(dtdAccess));
        setPropertyVerbose("jdk.internal.httpclient.disableHostnameVerification", String.valueOf(!isProdProfile()));
    }

    private void setPropertyVerbose(String name, String value) {
        System.setProperty(name, value);
        log.info(String.format("System property '%s' SET TO '%s'", name, value));
    }

    private String normalize(String dtdAccessValue) {
        return switch (dtdAccessValue) {
            case String s when s.contains(ALL) -> ALL;
            case String s when s.contains(NONE) -> NONE;
            default -> {
                Set<String> values = Arrays.stream(dtdAccessValue.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .filter(s -> !s.isEmpty())
                    .filter(s -> !ALL.equals(s))
                    .filter(s -> !NONE.equals(s))
                    .collect(Collectors.toSet());
                Set<String> set = values.stream().filter(OTHER_ACCESS_DTD_VALUES::contains).collect(Collectors.toSet());
                yield set.isEmpty() ? NONE : String.join(",", set);
            }
        };
    }

    public static boolean isProdProfile() {
        return PROD_PROFILES.contains(getQuarkusProfile());
    }

    public static String getQuarkusProfile() {
        return ConfigProvider.getConfig().getValue("quarkus.profile", String.class).toLowerCase();
    }
}
