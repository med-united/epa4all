package de.servicehealth.utils;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ServerUtils {

    private static final Logger log = LoggerFactory.getLogger(ServerUtils.class);

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
}
