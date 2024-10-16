package de.servicehealth.epa4all.vau;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import de.servicehealth.epa4all.utils.InstantDeSerializer;
import org.apache.commons.lang3.tuple.Pair;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class VauResponseReader {

    private final VauClient vauClient;
    private final Gson gson;

    public VauResponseReader(VauClient vauClient) {
        this.vauClient = vauClient;
        gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantDeSerializer())
            .disableHtmlEscaping()
            .create();
    }

    private boolean payloadCondition(byte[] vauBytes, int i) {
        return vauBytes[i] == 13 && vauBytes[i + 1] == 10 && vauBytes[i + 2] == 13 && vauBytes[i + 3] == 10;
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private String extractError(Optional<String> contentTypeOpt, byte[] bytes) {
        if (contentTypeOpt.isPresent()) {
            String contentType = contentTypeOpt.get();
            if ("application/cbor".equals(contentType)) {
                try {
                    JsonNode node = new CBORMapper().readTree(bytes);
                    String json = node.toString();
                    try {
                        gson.fromJson(json, GeneralError.class);
                        return json;
                    } catch (JsonSyntaxException e) {
                        return null;
                    }
                } catch (IOException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private Optional<String> findHeader(List<Pair<String, String>> headers, String headerName) {
        return headers.stream()
            .filter(p -> p.getKey().equalsIgnoreCase(headerName))
            .map(Pair::getValue)
            .findFirst();
    }

    public VauResponse read(int responseCode, List<Pair<String, String>> originHeaders, byte[] bytes) {
        Optional<String> contentTypeOpt = findHeader(originHeaders, "content-type");
        String generalError = extractError(contentTypeOpt, bytes);
        if (generalError != null) {
            return new VauResponse(responseCode, generalError, generalError.getBytes(UTF_8), originHeaders);
        } else {
            byte[] vauBytes = vauClient.getVauStateMachine().decryptVauMessage(bytes);

            int i = 0;
            while (!payloadCondition(vauBytes, i) && (i + 4 < vauBytes.length)) {
                i++;
            }

            byte[] headerBytes = new byte[i];
            System.arraycopy(vauBytes, 0, headerBytes, 0, i);
            int status = Integer.parseInt(new String(headerBytes, UTF_8).split("\n")[0].split(" ")[1]);
            List<Pair<String, String>> headers = Stream.of(new String(headerBytes, UTF_8).split("\n")).skip(1).map(s -> {
                String[] nameValue = s.split(": ");
                return Pair.of(nameValue[0].trim(), nameValue[1].trim());
            }).toList();

            byte[] payload = extractPayload(vauBytes, i, headers);
            return new VauResponse(status, null, payload, headers);
        }
    }

    private byte[] extractPayload(byte[] vauBytes, int i, List<Pair<String, String>> headers) {
        if (vauBytes.length - 4 > i) {
            byte[] payload = new byte[vauBytes.length - i - 4];
            System.arraycopy(vauBytes, i + 4, payload, 0, vauBytes.length - i - 4);
            Optional<Pair<String, String>> contentEncodingOpt = headers.stream()
                .filter(p -> p.getKey().equals("Content-Encoding"))
                .findFirst();
            return applyEncoding(contentEncodingOpt, payload);
        } else {
            return null;
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private byte[] applyEncoding(Optional<Pair<String, String>> contentEncodingOpt, byte[] payload) {
        if (contentEncodingOpt.isPresent()) {
            String contentEncoding = contentEncodingOpt.get().getValue();
            // compress, deflate, br, zstd
            if (contentEncoding.contains("gzip")) {
                try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(payload))) {
                    return gzipInputStream.readAllBytes();
                } catch (IOException e) {
                    return payload;
                }
            }
        }
        return payload;
    }
}
