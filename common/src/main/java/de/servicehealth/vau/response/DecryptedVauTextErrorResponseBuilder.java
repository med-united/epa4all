package de.servicehealth.vau.response;

import de.servicehealth.vau.VauResponse;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Optional;

import static de.servicehealth.utils.ServerUtils.findHeaderValue;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.cxf.helpers.HttpHeaderHelper.CONTENT_TYPE;

// 3
public class DecryptedVauTextErrorResponseBuilder extends AbstractVauResponseBuilder {

    @Override
    public VauResponse build(String vauCid, int responseCode, List<Pair<String, String>> headers, byte[] bytes) {
        String error = null;
        Optional<String> contentTypeOpt = findHeaderValue(headers, CONTENT_TYPE);
        if (responseCode >= 400 && contentTypeOpt.isPresent() && contentTypeOpt.get().contains("text")) {
            error = new String(bytes);
        }
        return error != null
            ? new VauResponse(responseCode, error, error.getBytes(UTF_8), headers)
            : super.build(vauCid, responseCode, headers, bytes);
    }
}
