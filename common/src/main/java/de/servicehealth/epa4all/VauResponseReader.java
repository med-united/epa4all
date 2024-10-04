package de.servicehealth.epa4all;

import org.apache.commons.lang3.tuple.Pair;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class VauResponseReader {

    private final VauClient vauClient;

    public VauResponseReader(VauClient vauClient) {
        this.vauClient = vauClient;
    }

    private boolean payloadCondition(byte[] vauBytes, int i) {
        return vauBytes[i] == 13 && vauBytes[i + 1] == 10 && vauBytes[i + 2] == 13 && vauBytes[i + 3] == 10;
    }

    public VauResponse read(byte[] bytes) {
        String rawMessage = new String(bytes, UTF_8);
        if (rawMessage.contains("error") || rawMessage.contains("ERROR") || rawMessage.contains("Error")) {
            throw new IllegalArgumentException(rawMessage);
        }
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

        byte[] payload = null;
        if (vauBytes.length - 4 > i) {
            payload = new byte[vauBytes.length - i - 4];
            System.arraycopy(vauBytes, i + 4, payload, 0, vauBytes.length - i - 4);

            Optional<Pair<String, String>> contentEncodingOpt = headers.stream()
                .filter(p -> p.getKey().equals("Content-Encoding"))
                .findFirst();

            try {
                payload = applyEncoding(contentEncodingOpt, payload);
            } catch (Exception ignored) {
            }
        }
        return new VauResponse(status, payload, headers);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private byte[] applyEncoding(Optional<Pair<String, String>> contentEncodingOpt, byte[] payload) throws IOException {
        byte[] bytes = payload;
        if (contentEncodingOpt.isPresent()) {
            String contentEncoding = contentEncodingOpt.get().getValue();
            if (contentEncoding.contains("gzip")) {
                try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(payload))) {
                    bytes = gzipInputStream.readAllBytes();
                }
            }
            // TODO - compress, deflate, br, zstd
        }
        return bytes;
    }
}
