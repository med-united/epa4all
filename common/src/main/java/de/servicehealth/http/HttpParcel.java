package de.servicehealth.http;

import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.cxf.ext.logging.MaskSensitiveHelper;
import org.apache.cxf.message.MessageImpl;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static de.servicehealth.utils.ServerUtils.decompress;
import static de.servicehealth.utils.ServerUtils.findHeaderValue;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toMap;
import static org.apache.cxf.message.Message.CONTENT_TYPE;

@Getter
public class HttpParcel {

    private static final int GAP = 4;

    private final String statusLine;
    private final String contentType;
    private final List<Pair<String, String>> headers;
    private final byte[] payload;
    private final MaskSensitiveHelper maskSensitiveHelper;

    public HttpParcel(String statusLine, List<Pair<String, String>> headers, byte[] payload) {
        this.statusLine = statusLine;
        this.headers = headers;
        this.payload = payload;

        contentType = findHeaderValue(headers, CONTENT_TYPE).orElse("");
        maskSensitiveHelper = new MaskSensitiveHelper();
    }

    public byte[] toBytes() {
        return ArrayUtils.addAll(getStatusLineWithHeaders(Set.of()).getBytes(UTF_8), payload);
    }

    public String toString(
        boolean base64,
        boolean showPayload
    ) {
        return toString(base64, showPayload, Set.of(), Set.of());
    }
    
    public String toString(
        boolean base64,
        boolean showPayload,
        Set<String> maskedHeaders,
        Set<String> maskedAttributes
    ) {
        String payloadString = "";
        if (showPayload && payload != null && payload.length > 0) {
            byte[] bytes = payload;
            if (base64) {
                bytes = Base64.getEncoder().encode(bytes);
            }
            payloadString = new String(bytes);
            MessageImpl fakeMessage = new MessageImpl();
            fakeMessage.put(CONTENT_TYPE, contentType);
            maskSensitiveHelper.setSensitiveElementNames(maskedAttributes);
            payloadString = maskSensitiveHelper.maskSensitiveElements(fakeMessage, payloadString);
        }
        return getStatusLineWithHeaders(maskedHeaders) + payloadString;
    }

    private String getStatusLineWithHeaders(Set<String> maskedHeaders) {
        Map<String, String> map = headers.stream()
            .filter(h -> h.getValue() != null && !h.getValue().trim().isEmpty() && !h.getValue().equals("null"))
            .map(h -> Pair.of(h.getKey(), h.getValue()))
            .collect(toMap(Pair::getKey, Pair::getValue));

        maskSensitiveHelper.maskHeaders(map, maskedHeaders);
        String headersString = map.entrySet().stream()
            .map(e -> String.format("%s: %s", e.getKey(), e.getValue()))
            .collect(Collectors.joining("\r\n"));

        return statusLine + "\r\n" + headersString + "\r\n\r\n";
    }

    public boolean isResponse() {
        return statusLine.startsWith("HTTP");
    }

    public int getStatus() {
        if (isResponse()) {
            try {
                return Integer.parseInt(statusLine.split(" ")[1].trim());
            } catch (Exception e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        } else {
            throw new IllegalStateException("Not a HttpResponse");
        }
    }

    public String getMethod() {
        if (isResponse()) {
            throw new IllegalStateException("Not a HttpRequest");
        }
        return statusLine.split(" ")[0].trim();
    }

    public String getPath() {
        if (isResponse()) {
            throw new IllegalStateException("Not a HttpRequest");
        }
        return statusLine.split(" ")[1].trim();
    }

    public static HttpParcel from(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("HttpParcel payload is empty");
        }
        int headersBoundary = 0;
        while (!payloadCondition(bytes, headersBoundary) && (headersBoundary + GAP < bytes.length)) {
            headersBoundary++;
        }

        byte[] headerBytes = new byte[headersBoundary];
        System.arraycopy(bytes, 0, headerBytes, 0, headersBoundary);

        List<String> headerLines = getHeaderLines(headerBytes);
        String statusLine = headerLines.get(0);
        List<Pair<String, String>> headers = getHeaders(headerLines);
        byte[] payload = extractPayload(bytes, headersBoundary);

        return new HttpParcel(statusLine, headers, payload);
    }

    private static boolean payloadCondition(byte[] bytes, int i) {
        return bytes[i] == 13 && bytes[i + 1] == 10 && bytes[i + 2] == 13 && bytes[i + 3] == 10;
    }

    private static List<String> getHeaderLines(byte[] headerBytes) {
        String source = new String(headerBytes, UTF_8);
        String[] strings = source.contains("\r\n")
            ? source.split("\r\n")
            : source.split("\n");
        
        return Arrays.asList(strings);
    }

    // Using of Map can drop origin duplicates headers
    private static List<Pair<String, String>> getHeaders(List<String> headerLines) {
        return headerLines.stream().skip(1).map(s -> {
            String[] nameValue = s.split(": ");
            return Pair.of(nameValue[0].trim(), nameValue[1].trim());
        }).toList();
    }

    private static byte[] extractPayload(byte[] bytes, int headersBoundary) {
        if (bytes.length - GAP > headersBoundary) {
            byte[] payload = new byte[bytes.length - headersBoundary - GAP];
            System.arraycopy(bytes, headersBoundary + GAP, payload, 0, bytes.length - headersBoundary - GAP);
            return decompress(payload);
        } else {
            return null;
        }
    }
}
