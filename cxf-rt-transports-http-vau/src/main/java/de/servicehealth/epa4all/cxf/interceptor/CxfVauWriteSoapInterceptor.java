package de.servicehealth.epa4all.cxf.interceptor;

import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauFacade;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.InterceptorChain;
import org.apache.cxf.interceptor.StaxOutInterceptor;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.transport.http.Address;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static de.servicehealth.epa4all.cxf.interceptor.InterceptorUtils.excludeInterceptors;
import static de.servicehealth.epa4all.cxf.transport.HTTPClientVauConduit.VAU_METHOD_PATH;
import static de.servicehealth.vau.VauClient.VAU_CID;
import static de.servicehealth.vau.VauClient.VAU_NON_PU_TRACING;
import static de.servicehealth.vau.VauClient.VAU_NP;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.cxf.message.Message.PROTOCOL_HEADERS;
import static org.apache.cxf.phase.Phase.PRE_STREAM;

public class CxfVauWriteSoapInterceptor extends AbstractPhaseInterceptor<Message> {

    private final VauFacade vauFacade;
    private static final Logger log = Logger.getLogger(CxfVauWriteSoapInterceptor.class.getName());

    public CxfVauWriteSoapInterceptor(VauFacade vauFacade) {
        super(PRE_STREAM);
        addBefore(StaxOutInterceptor.class.getName());
        this.vauFacade = vauFacade;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleMessage(Message message) throws Fault {
        try {
            TreeMap<String, List<String>> httpHeaders = (TreeMap<String, List<String>>) message.get(PROTOCOL_HEADERS);
            List<String> vauPathHeaders = httpHeaders.remove(VAU_METHOD_PATH);
            List<String> vauCidHeaders = httpHeaders.remove(VAU_CID);
            List<String> backendHeaders = httpHeaders.remove(X_BACKEND);

            if (!vauFacade.isTracingEnabled()) {
                httpHeaders.remove(VAU_NON_PU_TRACING);
            }

            String path = (vauPathHeaders == null || vauPathHeaders.isEmpty()) ? "undefined" : vauPathHeaders.getFirst();
            String vauCid = (vauCidHeaders == null || vauCidHeaders.isEmpty()) ? "undefined" : vauCidHeaders.getFirst();
            String backend = (backendHeaders == null || backendHeaders.isEmpty()) ? "undefined" : backendHeaders.getFirst();

            String additionalHeaders = httpHeaders.entrySet()
                .stream()
                .filter(p -> !p.getKey().equalsIgnoreCase(CONTENT_TYPE))
                .filter(p -> !p.getKey().equalsIgnoreCase(ACCEPT))
                .map(p -> p.getKey() + ": " + p.getValue().getFirst())
                .collect(Collectors.joining("\r\n"));

            if (!additionalHeaders.isBlank()) {
                additionalHeaders += "\r\n";
            }

            String keepAlive = additionalHeaders.contains("Keep-Alive") ? "" : "Connection: Keep-Alive\r\n";

            OutputStream os = message.getContent(OutputStream.class);
            CachedStream cs = new CachedStream();
            message.setContent(OutputStream.class, cs);

            InterceptorChain interceptorChain = excludeInterceptors(
                message
            );

            interceptorChain.doIntercept(message);

            cs.flush();

            CachedOutputStream csNew = (CachedOutputStream) message.getContent(OutputStream.class);
            message.setContent(OutputStream.class, os);
            String payload = new String(csNew.getBytes(), UTF_8);
            byte[] full = payload.getBytes();

            Address address = (Address) message.get("http.connection.address");
            String fullString = new String(full);

            String contentType = String.valueOf(message.get(CONTENT_TYPE));
            String insurantId = String.valueOf(message.get(X_INSURANT_ID));
            String userAgent = String.valueOf(message.get(X_USER_AGENT));
            String np = String.valueOf(message.get(VAU_NP));

            String headers = prepareContentHeaders(
                insurantId,
                np,
                userAgent,
                contentType,
                fullString.getBytes().length
            );

            byte[] httpRequest = (path + " HTTP/1.1\r\n"
                + "Host: " + address.getURL().getHost() + "\r\n"
                + additionalHeaders + keepAlive
                + headers
            ).getBytes();

            byte[] content = ArrayUtils.addAll(httpRequest, fullString.getBytes());
            VauClient vauClient = vauFacade.getVauClient(vauCid);
            byte[] vauMessage = vauClient.getVauStateMachine().encryptVauMessage(content);

            message.put("org.apache.cxf.message.Message.ENCODING", null);

            try {
                os.write(vauMessage, 0, vauMessage.length);
            } finally {
                cs.close();
                os.flush();
                os.close();
            }
        } catch (Exception e) {
            throw new Fault(e);
        }
    }

    private String prepareContentHeaders(
        String insurantId,
        String np,
        String userAgent,
        String contentType,
        int length
    ) {
        String headers = "Content-Type: " + contentType;
        headers += "\r\nContent-Length: " + length;
        headers += "\r\n" + X_USER_AGENT + ": " + userAgent;
        if (insurantId != null) {
            headers += "\r\n" + X_INSURANT_ID + ": " + insurantId;
        }
        if (np != null) {
            headers += "\r\n" + VAU_NP + ": " + np;
        }
        headers += "\r\n\r\n";
        return headers;
    }

    private class CachedStream extends CachedOutputStream {
        public CachedStream() {
            // forces CachedOutputStream to keep the whole content in-memory.
            super(1024 * 1024 * (long) 1024);
        }

        protected void doFlush() throws IOException {
            currentStream.flush();
        }
    }
}
