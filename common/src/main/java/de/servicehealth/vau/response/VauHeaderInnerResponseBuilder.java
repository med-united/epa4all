package de.servicehealth.vau.response;

import de.servicehealth.http.HttpParcel;
import de.servicehealth.vau.VauResponse;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

import static de.servicehealth.utils.ServerUtils.findHeaderValue;
import static de.servicehealth.vau.VauClient.VAU_ERROR;
import static java.nio.charset.StandardCharsets.UTF_8;

// 4
public class VauHeaderInnerResponseBuilder extends AbstractVauResponseBuilder {

    @Override
    public VauResponse build(String vauCid, int responseCode, List<Pair<String, String>> headers, byte[] bytes) {
        HttpParcel httpResponse = HttpParcel.from(bytes);
        List<Pair<String, String>> innerHeaders = httpResponse.getHeaders();
        String error = findHeaderValue(innerHeaders, VAU_ERROR).orElse(null);
        if (error != null) {
            return new VauResponse(httpResponse.getStatus(), error, error.getBytes(UTF_8), innerHeaders, true);
        } else {
            int status = httpResponse.getStatus();
            byte[] payload = httpResponse.getPayload();
            if (status >= 400) {
                error = payload == null ? "no error description" : new String(payload);
                return new VauResponse(status, error, error.getBytes(UTF_8), innerHeaders, true);
            } else {
                return new VauResponse(status, null, payload, innerHeaders, true);
            }
        }
    }
}
