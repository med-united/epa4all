package de.servicehealth.vau.response;

import de.servicehealth.vau.VauResponse;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Optional;

import static de.servicehealth.utils.ServerUtils.findHeaderValue;
import static de.servicehealth.vau.VauClient.VAU_ERROR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.cxf.helpers.HttpHeaderHelper.CONTENT_TYPE;

// 1
public class RawVauTextErrorResponseBuilder extends AbstractVauResponseBuilder {

    @Override
    public VauResponse build(String vauCid, int responseCode, List<Pair<String, String>> headers, byte[] bytes) {
        String error = null;
        Optional<String> contentTypeOpt = findHeaderValue(headers, CONTENT_TYPE);
        Optional<String> vauErrorOpt = findHeaderValue(headers, VAU_ERROR);
        if (responseCode >= 400 && contentTypeOpt.isPresent() && CONTENT_TYPES.stream().anyMatch(contentTypeOpt.get()::contains)) {
            String source = new String(bytes);
            error = responseCode + " " + source;
        } else if (vauErrorOpt.isPresent()) {
            error = vauErrorOpt.get();
        }
        return error != null
            ? new VauResponse(responseCode, error, error.getBytes(UTF_8), headers, false)
            : super.build(vauCid, responseCode, headers, bytes);
    }
}
