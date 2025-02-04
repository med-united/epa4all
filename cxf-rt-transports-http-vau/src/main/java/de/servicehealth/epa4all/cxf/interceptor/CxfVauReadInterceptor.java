package de.servicehealth.epa4all.cxf.interceptor;

import de.servicehealth.vau.VauFacade;
import de.servicehealth.vau.VauResponse;
import de.servicehealth.vau.VauResponseReader;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Set;

import static de.servicehealth.epa4all.cxf.interceptor.InterceptorUtils.getProtocolHeaders;
import static de.servicehealth.epa4all.cxf.interceptor.InterceptorUtils.putProtocolHeader;
import static de.servicehealth.utils.ServerUtils.isAuthError;
import static de.servicehealth.vau.VauClient.VAU_CID;
import static de.servicehealth.vau.VauClient.VAU_ERROR;
import static de.servicehealth.vau.VauClient.VAU_NO_SESSION;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.HttpHeaders.LOCATION;
import static org.apache.cxf.message.Message.RESPONSE_CODE;

public class CxfVauReadInterceptor extends AbstractPhaseInterceptor<Message> {

    private static final Logger log = LoggerFactory.getLogger(CxfVauReadInterceptor.class.getName());

    public static final Set<String> MEDIA_TYPES = Set.of(
        "application/octet-stream", "application/pdf", "image/png", "image/jpeg", "image/gif", "image/bmp"
    );

    private final VauFacade vauFacade;
    private final VauResponseReader vauResponseReader;

    public CxfVauReadInterceptor(VauFacade vauFacade) {
        super(Phase.PROTOCOL);
        this.vauFacade = vauFacade;

        vauResponseReader = new VauResponseReader(vauFacade);
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        try {
            InputStream inputStream = message.getContent(InputStream.class);
            Integer responseCode = (Integer) message.get(RESPONSE_CODE);

            String vauCid = (String) message.getExchange().get(VAU_CID);
            byte[] vauPayload = inputStream.readAllBytes();
            VauResponse vauResponse = vauResponseReader.read(
                vauCid, responseCode, getProtocolHeaders(message), vauPayload
            );
            restoreHeaders(vauResponse, message, Set.of(LOCATION, CONTENT_TYPE, CONTENT_LENGTH));
            String error = vauResponse.error();
            if (error != null) {
                putProtocolHeader(message, VAU_ERROR, error);
                boolean noUserSession = isAuthError(error);
                if (noUserSession) {
                    putProtocolHeader(message, VAU_NO_SESSION, "true");
                }
                vauFacade.handleVauSession(vauCid, noUserSession, vauResponse.decrypted());
            }
            String operation = (String) message.getExchange().get("org.apache.cxf.resource.operation.name");
            byte[] payload = vauResponse.payload();
            if (payload != null) {
                vauResponse.headers().stream()
                    .filter(p -> p.getKey().equalsIgnoreCase(CONTENT_TYPE))
                    .findFirst().ifPresent(h -> {
                        String contentType = h.getValue();
                        message.put(CONTENT_TYPE, contentType);
                        if (!MEDIA_TYPES.contains(contentType)) {
                            String content = new String(payload);
                            content = content.substring(0, Math.min(200, content.length())) + " ********* ";
                            log.info(String.format("[%s] Response PAYLOAD: %s", operation, content));
                        }
                    });

                message.setContent(InputStream.class, new ByteArrayInputStream(payload));
                putProtocolHeader(message, CONTENT_LENGTH, payload.length);
            }
        } catch (Exception e) {
            throw new Fault(e);
        }
    }

    private void restoreHeaders(VauResponse vauResponse, Message message, Set<String> headersNames) {
        vauResponse.headers().stream()
            .filter(p -> headersNames.stream().anyMatch(headersName -> headersName.equalsIgnoreCase(p.getKey())))
            .forEach(p -> putProtocolHeader(message, p.getKey(), p.getValue()));
    }
}
