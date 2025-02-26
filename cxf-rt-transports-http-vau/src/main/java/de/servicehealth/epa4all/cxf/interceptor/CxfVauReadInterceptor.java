package de.servicehealth.epa4all.cxf.interceptor;

import de.servicehealth.vau.VauFacade;
import de.servicehealth.vau.VauResponse;
import de.servicehealth.vau.VauResponseReader;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.io.CachedOutputStream;
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
import static de.servicehealth.vau.VauClient.VAU_STATUS;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.HttpHeaders.LOCATION;
import static org.apache.cxf.message.Message.RESPONSE_CODE;

public class CxfVauReadInterceptor extends AbstractPhaseInterceptor<Message> {

    private static final Logger log = LoggerFactory.getLogger(CxfVauReadInterceptor.class.getName());

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

            VauResponse vauResponse = vauResponseReader.read(vauCid, responseCode, getProtocolHeaders(message), vauPayload);
            restoreProtocolHeaders(vauResponse, message, Set.of(LOCATION, CONTENT_TYPE, CONTENT_LENGTH));
            putProtocolHeader(message, VAU_STATUS, vauResponse.status());
            String error = vauResponse.error();
            if (error != null) {
                boolean noUserSession = isAuthError(error);
                putProtocolHeader(message, VAU_ERROR, error);
                if (noUserSession) {
                    putProtocolHeader(message, VAU_NO_SESSION, "true");
                }
                vauFacade.handleVauSessionError(vauCid, noUserSession, vauResponse.decrypted());
            }
            byte[] payload = vauResponse.payload();
            if (payload != null) {
                vauResponse.headers().stream()
                    .filter(p -> p.getKey().equalsIgnoreCase(CONTENT_TYPE))
                    .findFirst()
                    .ifPresent(h -> message.put(CONTENT_TYPE, h.getValue()));

                message.setContent(InputStream.class, new ByteArrayInputStream(payload));
                replaceLoggingFeatureStream(message, payload);

                putProtocolHeader(message, CONTENT_LENGTH, payload.length);
            }
        } catch (Exception e) {
            throw new Fault(e);
        }
    }

    private void replaceLoggingFeatureStream(Message message, byte[] payload) {
        CachedOutputStream os = new CachedOutputStream();
        try {
            IOUtils.copyAtLeast(new ByteArrayInputStream(payload), os, 300);
            os.flush();
            message.setContent(CachedOutputStream.class, os);
        } catch (Exception e) {
            log.error("Unable to prepare LoggingFeature CachedOutputStream from origin", e);
        } 
    }

    private void restoreProtocolHeaders(VauResponse vauResponse, Message message, Set<String> headersNames) {
        vauResponse.headers().stream()
            .filter(p -> headersNames.stream().anyMatch(headersName -> headersName.equalsIgnoreCase(p.getKey())))
            .forEach(p -> putProtocolHeader(message, p.getKey(), p.getValue()));
    }
}
