package de.servicehealth.epa4all.cxf.interceptor;

import de.servicehealth.vau.VauClient;
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

import static de.servicehealth.epa4all.cxf.client.ClientFactory.USER_AGENT;
import static de.servicehealth.epa4all.cxf.interceptor.InterceptorUtils.excludeInterceptors;
import static de.servicehealth.epa4all.cxf.transport.HTTPClientVauConduit.VAU_METHOD_PATH;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.cxf.message.Message.PROTOCOL_HEADERS;
import static org.apache.cxf.phase.Phase.PRE_STREAM;

public class CxfVauWriteSoapInterceptor extends AbstractPhaseInterceptor<Message> {

    private final VauClient vauClient;
    private static final Logger log = Logger.getLogger(CxfVauWriteSoapInterceptor.class.getName());

    public CxfVauWriteSoapInterceptor(VauClient vauClient) {
        super(PRE_STREAM);
        addBefore(StaxOutInterceptor.class.getName());
        this.vauClient = vauClient;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleMessage(Message message) throws Fault {
        try {
            System.out.println(message);

            TreeMap<String, List<String>> httpHeaders = (TreeMap<String, List<String>>) message.get(PROTOCOL_HEADERS);
            List<String> vauPathHeaders = httpHeaders.remove(VAU_METHOD_PATH);

            // inbound message
            // if(vauPathHeaders == null) {
            //	return;
            // }
            String path = (vauPathHeaders == null || vauPathHeaders.isEmpty()) ? "undefined" : vauPathHeaders.getFirst();

            String additionalHeaders = httpHeaders.entrySet()
                .stream()
                .filter(p -> !p.getKey().equals(CONTENT_TYPE))
                .filter(p -> !p.getKey().equals(ACCEPT))
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

            byte[] httpRequest = (path + " HTTP/1.1\r\n"
                + "Host: " + address.getURL().getHost() + "\r\n"
                + additionalHeaders + keepAlive
                + prepareContentHeaders(message.get(CONTENT_TYPE).toString(), fullString.getBytes().length)).getBytes();

            byte[] content = ArrayUtils.addAll(httpRequest, fullString.getBytes());

            String soapMessageAsString = new String(content);

            log.info("Inner VAU Request:" + soapMessageAsString);

            byte[] vauMessage = vauClient.getVauStateMachine().encryptVauMessage(soapMessageAsString.getBytes());
            // httpHeaders.put(CONTENT_LENGTH, List.of(String.valueOf(vauMessage.length)));

            message.put("org.apache.cxf.message.Message.ENCODING", null);

            // if (os instanceof HTTPClientVauConduit.VauHttpClientWrappedOutputStream vwos) {
            //    vwos.setFixedLengthStreamingMode(vauMessage.length);
            //}
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

    private String prepareContentHeaders(String contentType, int length) {
        String headers = "Content-Type: " + contentType;
        headers += "\r\nContent-Length: " + length;
        headers += "\r\nx-useragent: " + USER_AGENT;
        if (vauClient.getXInsurantId() != null) {
            headers += "\r\nx-insurantid: " + vauClient.getXInsurantId();
        }
        if (vauClient.getNp() != null) {
            headers += "\r\nVAU-NP: " + vauClient.getNp();
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

        protected void doClose() throws IOException {
        }

        protected void onWrite() throws IOException {
        }
    }
}