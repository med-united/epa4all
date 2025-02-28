package de.servicehealth.vau;

import de.servicehealth.http.HttpParcel;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static de.servicehealth.utils.CborUtils.getGeneralErrorAsString;
import static de.servicehealth.utils.ServerUtils.APPLICATION_CBOR;
import static de.servicehealth.utils.ServerUtils.findHeaderValue;
import static de.servicehealth.vau.VauClient.VAU_ERROR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.cxf.helpers.HttpHeaderHelper.CONTENT_TYPE;

public class VauResponseReader {

    private static final Logger log = LoggerFactory.getLogger(VauResponseReader.class.getName());

    private static final Set<String> CONTENT_TYPES = Set.of("text", "json");

    private final VauFacade vauFacade;

    public VauResponseReader(VauFacade vauFacade) {
        this.vauFacade = vauFacade;
    }

    private VauResponse getErrorResponse(int outerStatus, List<Pair<String, String>> headers, byte[] bytes) {
        Optional<String> vauErrorOpt = findHeaderValue(headers, VAU_ERROR);
        if (vauErrorOpt.isPresent()) {
            return new VauResponse(outerStatus, vauErrorOpt.get(), vauErrorOpt.get().getBytes(UTF_8), headers, false);
        }
        Optional<String> contentTypeOpt = findHeaderValue(headers, CONTENT_TYPE);
        if (contentTypeOpt.isPresent()) {
            if (outerStatus >= 400 && CONTENT_TYPES.stream().anyMatch(contentTypeOpt.get()::contains)) {
                String error = new String(bytes);
                new VauResponse(outerStatus, error, error.getBytes(UTF_8), headers, false);
            }
            if (contentTypeOpt.get().equals(APPLICATION_CBOR)) {
                String generalError = getGeneralErrorAsString(bytes);
                if (generalError != null) {
                    return new VauResponse(outerStatus, generalError, generalError.getBytes(UTF_8), headers, false);
                }
            }
        }
        return null;
    }

    private String getErrorMessage(Throwable e) {
        return e.getMessage() == null ? "undefined" : e.getMessage();
    }

    public VauResponse read(String vauCid, int outerStatus, InputStream inputStream, List<Pair<String, String>> headers) {
        boolean decrypted = false;
        try {
            byte[] vauBytes = inputStream.readAllBytes();
            VauResponse errorResponse = getErrorResponse(outerStatus, headers, vauBytes);
            if (errorResponse != null) {
                return errorResponse;
            }
            VauClient vauClient = vauFacade.find(vauCid);
            if (vauClient == null) {
                String error = "Vau request read timed out";
                return new VauResponse(outerStatus, error, error.getBytes(UTF_8), headers, false);
            }
            byte[] payloadBytes = vauClient.decryptVauMessage(vauBytes);
            decrypted = true;
            
            HttpParcel httpResponse = HttpParcel.from(payloadBytes);
            List<Pair<String, String>> innerHeaders = httpResponse.getHeaders();
            int innerStatusCode = httpResponse.getStatus();
            byte[] payload = httpResponse.getPayload();
            if (innerStatusCode >= 400) {
                String error = payload == null ? "undefined" : new String(payload);
                return new VauResponse(innerStatusCode, error, error.getBytes(UTF_8), innerHeaders, true);
            }
            return new VauResponse(innerStatusCode, null, payload, innerHeaders, true);
        } catch (Throwable e) {
            log.error("Error while VauResponseReader.read", e);
            String error = getErrorMessage(e);
            return new VauResponse(outerStatus, error, error.getBytes(UTF_8), headers, decrypted);
        }
    }
}
