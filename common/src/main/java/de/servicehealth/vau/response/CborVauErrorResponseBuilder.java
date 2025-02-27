package de.servicehealth.vau.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import de.servicehealth.gson.InstantDeSerializer;
import de.servicehealth.vau.GeneralError;
import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauFacade;
import de.servicehealth.vau.VauResponse;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.servicehealth.utils.ServerUtils.findHeaderValue;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.cxf.helpers.HttpHeaderHelper.CONTENT_TYPE;

// 2
public class CborVauErrorResponseBuilder extends AbstractVauResponseBuilder {

    private static final Logger log = LoggerFactory.getLogger(CborVauErrorResponseBuilder.class.getName());

    private final VauFacade vauFacade;
    private final Gson gson;

    public CborVauErrorResponseBuilder(VauFacade vauFacade) {
        this.vauFacade = vauFacade;

        gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantDeSerializer())
            .disableHtmlEscaping()
            .create();
    }

    @Override
    public VauResponse build(String vauCid, int responseCode, List<Pair<String, String>> headers, byte[] bytes) {
        String error = null;
        Optional<String> contentTypeOpt = findHeaderValue(headers, CONTENT_TYPE);
        if (contentTypeOpt.isPresent() && contentTypeOpt.get().equals("application/cbor")) {
            try {
                JsonNode node = new CBORMapper().readTree(bytes);
                String json = node.toString();
                try {
                    gson.fromJson(json, GeneralError.class);
                    error = json;
                } catch (JsonSyntaxException ignored) {
                }
            } catch (IOException ignored) {
            }
        }
        if (error != null) {
            return new VauResponse(responseCode, error, error.getBytes(UTF_8), headers, false);
        } else {
            VauClient vauClient = vauFacade.find(vauCid);
            if (vauClient == null) {
                error = "Vau request read timed out";
                return new VauResponse(responseCode, error, error.getBytes(UTF_8), headers, false);
            }
            byte[] decryptedBytes;
            try {
                decryptedBytes = vauClient.decryptVauMessage(bytes);
            } catch (Throwable e) {
                log.error("Error while CborVauErrorResponseBuilder.decryptVauMessage", e);
                String errorMsg = e.getMessage();
                if (errorMsg == null) {
                    errorMsg = "Empty error";
                }
                return new VauResponse(responseCode, errorMsg, errorMsg.getBytes(UTF_8), headers, false);
            }
            return super.build(vauCid, responseCode, headers, decryptedBytes);
        }
    }
}
