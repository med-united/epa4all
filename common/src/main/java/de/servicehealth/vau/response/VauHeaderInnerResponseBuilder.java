package de.servicehealth.vau.response;

import de.servicehealth.vau.VauResponse;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.stream.Stream;

import static de.servicehealth.utils.ServerUtils.decompress;
import static de.servicehealth.vau.VauClient.VAU_ERROR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.cxf.helpers.HttpHeaderHelper.CONTENT_ENCODING;

// 4
public class VauHeaderInnerResponseBuilder extends AbstractVauResponseBuilder {

    private boolean payloadCondition(byte[] vauBytes, int i) {
        return vauBytes[i] == 13 && vauBytes[i + 1] == 10 && vauBytes[i + 2] == 13 && vauBytes[i + 3] == 10;
    }

    @Override
    public VauResponse build(String vauCid, int responseCode, List<Pair<String, String>> headers, byte[] bytes) {
        int i = 0;
        while (!payloadCondition(bytes, i) && (i + 4 < bytes.length)) {
            i++;
        }

        byte[] headerBytes = new byte[i];
        System.arraycopy(bytes, 0, headerBytes, 0, i);
        int status = getStatus(headerBytes);
        List<Pair<String, String>> innerHeaders = getInnerHeaders(headerBytes);
        String error = findHeaderValue(innerHeaders, VAU_ERROR).orElse(null);
        if (error != null) {
            return new VauResponse(status, error, error.getBytes(UTF_8), innerHeaders);
        } else {
            byte[] payload = extractPayload(bytes, i, innerHeaders);
            if (status >= 400) {
                error = payload == null ? "no error description" : new String(payload);
                return new VauResponse(status, error, error.getBytes(UTF_8), innerHeaders);
            } else {
                return new VauResponse(status, null, payload, innerHeaders);
            }
        }
    }

    private int getStatus(byte[] headerBytes) {
        return Integer.parseInt(new String(headerBytes, UTF_8).split("\n")[0].split(" ")[1]);
    }

    private List<Pair<String, String>> getInnerHeaders(byte[] headerBytes) {
        return Stream.of(new String(headerBytes, UTF_8).split("\n")).skip(1).map(s -> {
            String[] nameValue = s.split(": ");
            return Pair.of(nameValue[0].trim(), nameValue[1].trim());
        }).toList();
    }

    private byte[] extractPayload(byte[] vauBytes, int i, List<Pair<String, String>> headers) {
        if (vauBytes.length - 4 > i) {
            byte[] payload = new byte[vauBytes.length - i - 4];
            System.arraycopy(vauBytes, i + 4, payload, 0, vauBytes.length - i - 4);

            return findHeaderValue(headers, CONTENT_ENCODING).map(contentEncoding -> {
                if (contentEncoding.contains("gzip")) {
                    return decompress(payload);
                } else {
                    return payload;
                }
            }).orElse(payload);
        } else {
            return null;
        }
    }
}
