package de.servicehealth.utils;

import java.util.Map;
import java.util.Set;

import static de.servicehealth.utils.ServerUtils.APPLICATION_PDF;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;
import static jakarta.ws.rs.core.MediaType.TEXT_HTML;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static jakarta.ws.rs.core.MediaType.WILDCARD;

public class MimeHelper {

    private static final Map<String, Set<String>> MIME_MAP = Map.of(
        TEXT_PLAIN, Set.of(".txt", ".log"),
        TEXT_HTML, Set.of(".htm", ".html"),
        APPLICATION_XML, Set.of(".xml", ".xhtml"),
        APPLICATION_OCTET_STREAM, Set.of(".bin"),
        APPLICATION_JSON, Set.of(".json"),
        APPLICATION_PDF, Set.of(".pdf")
    );

    private MimeHelper() {
    }

    public static String resolveMimeType(String fileName) {
        return MIME_MAP.entrySet().stream()
            .filter(e -> e.getValue().stream().anyMatch(fileName::endsWith))
            .findFirst()
            .map(Map.Entry::getKey).orElse(WILDCARD);
    }
}
