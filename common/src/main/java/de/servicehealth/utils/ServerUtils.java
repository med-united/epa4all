package de.servicehealth.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.tuple.Pair;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static de.servicehealth.vau.VauFacade.AUTH_ERRORS;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ServerUtils {

    public static final String APPLICATION_PDF = "application/pdf";
    public static final String APPLICATION_CBOR = "application/cbor";

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ServerUtils() {
    }

    public static String getBaseUrl(String url) {
        URI uri = URI.create(url.replace("+vau", ""));
        String scheme = uri.getScheme() == null ? "" : uri.getScheme() + "://";
        String host = uri.getHost();
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return scheme + host + port;
    }

    public static String getBackendUrl(String backend, String serviceUrl) {
        return serviceUrl.replace("[epa-backend]", backend);
    }

    public static void unzipAndSaveDataToFile(byte[] bytes, File outputFile) throws IOException {
        if (!outputFile.exists()) {
            outputFile.createNewFile();
        }
        try (FileOutputStream os = new FileOutputStream(outputFile)) {
            os.write(decompress(bytes));
        }
    }

    public static byte[] compress(byte[] bytes) {
        try {
            new GZIPInputStream(new ByteArrayInputStream(bytes));
            return bytes;
        } catch (Exception e) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(os)) {
                gzipOut.write(bytes);
                gzipOut.finish();

                return os.toByteArray();
            } catch (Exception ignored) {
                return bytes;
            }
        }
    }

    public static boolean isAuthError(String error) {
        return AUTH_ERRORS.stream().anyMatch(error::contains);
    }

    public static byte[] decompress(final byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new byte[0];
        }
        try (GZIPInputStream gzipIn = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return gzipIn.readAllBytes();
        } catch (Exception e) {
            return bytes;
        }
    }

    public static Date asDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
    }

    public static Throwable getOriginalCause(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    @SuppressWarnings("rawtypes")
    public static List<Pair<String, String>> extractHeaders(Map<String, Object> httpHeaders, Set<String> excluded) {
        return new ArrayList<>(httpHeaders.entrySet()
            .stream()
            .filter(p -> excluded.isEmpty() || excluded.stream().noneMatch(ex -> p.getKey().equalsIgnoreCase(ex)))
            .map(p -> Pair.of(p.getKey(), String.valueOf(((List) p.getValue()).getFirst())))
            .toList());
    }

    public static String getHeaderValue(List<Pair<String, String>> headers, String headerName) {
        return findHeaderValue(headers, headerName).orElseThrow(() ->
            new IllegalStateException(String.format("'%s' is not defined", headerName))
        );
    }

    public static Optional<String> findHeaderValue(List<Pair<String, String>> headers, String headerName) {
        return findHeader(headers, headerName).map(Pair::getValue);
    }

    public static String getHeaderValue(Map<String, List<Object>> headers, String headerName) {
        return findHeaderValue(headers, headerName).orElseThrow(() ->
            new IllegalStateException(String.format("'%s' is not defined", headerName))
        );
    }

    public static Optional<String> findHeaderValue(Map<String, List<Object>> headers, String headerName) {
        return findHeader(headers, headerName).map(Pair::getValue);
    }

    public static Optional<Pair<String, String>> findHeader(List<Pair<String, String>> headers, String headerName) {
        return headers.stream()
            .filter(p -> p.getKey().equalsIgnoreCase(headerName))
            .findFirst();
    }

    public static Optional<Pair<String, String>> findHeader(Map<String, List<Object>> headers, String headerName) {
        return headers.entrySet().stream()
            .filter(h -> h.getKey().equalsIgnoreCase(headerName))
            .map(h -> {
                List<Object> list = h.getValue();
                String value = list == null || list.isEmpty() ? null : String.valueOf(list.get(0));
                return Pair.of(h.getKey(), value);
            })
            .findFirst();
    }

    public static <T> T from(JsonNode jsonNode, Class<T> clazz) throws IOException {
        return OBJECT_MAPPER.readerFor(clazz).readValue(jsonNode);
    }

    public static String asString(Object object) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(object);
    }

    public static JsonNode extractJsonNode(Object entity) {
        try {
            return switch (entity) {
                case InputStream is -> OBJECT_MAPPER.readTree(new String(is.readAllBytes(), UTF_8));
                case String payload -> OBJECT_MAPPER.readTree(payload);
                case Collection<?> collection -> createArrayNode(collection);
                case ObjectNode objectNode -> objectNode;
                case ArrayNode arrayNode -> arrayNode;
                case null, default -> {
                    Map<String, String> map = Map.of("entity", entity == null ? "NULL" : "Type: " + entity.getClass().getName());
                    yield createObjectNode(map);
                }
            };
        } catch (Exception e) {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("error", e.getMessage());
            return node;
        }
    }

    public static ArrayNode createArrayNode(Collection<?> items) {
        ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
        items.forEach(obj -> {
            if (obj instanceof JsonNode jsonNode) {
                arrayNode.add(jsonNode);
            } else {
                arrayNode.add(extractJsonNode(obj));
            }
        });
        return arrayNode;
    }

    public static String pretty(Collection<JsonNode> nodes) {
        return createArrayNode(nodes).toPrettyString();
    }

    public static JsonNode createObjectNode(Map<String, ?> attributes) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        attributes.forEach((key, value) -> {
            switch (value) {
                case JsonNode jsonNode -> node.set(key, jsonNode);
                case Short shortValue -> node.put(key, shortValue);
                case Integer intValue -> node.put(key, intValue);
                case Long longValue -> node.put(key, longValue);
                case Float floatValue -> node.put(key, floatValue);
                case Double doubleValue -> node.put(key, doubleValue);
                case BigInteger biValue -> node.put(key, biValue);
                case BigDecimal bdValue -> node.put(key, bdValue);
                case String strValue -> node.put(key, strValue);
                case Boolean boolValue -> node.put(key, boolValue);
                case byte[] bytes -> node.put(key, bytes);
                case null, default -> node.put(key, String.valueOf(value));
            }
        });
        return node;
    }
}
