package de.servicehealth.epa4all.cxf.interceptor;

import de.servicehealth.vau.VauClient;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.cxf.binding.soap.interceptor.SoapOutInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.InterceptorChain;
import org.apache.cxf.interceptor.StaxOutInterceptor;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static de.servicehealth.epa4all.cxf.interceptor.InterceptorUtils.excludeInterceptors;
import static de.servicehealth.epa4all.cxf.transport.HTTPClientVauConduit.VAU_METHOD_PATH;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.cxf.message.Message.PROTOCOL_HEADERS;
import static org.apache.cxf.phase.Phase.PRE_STREAM;

public class CxfVauWriteSoapInterceptor extends AbstractPhaseInterceptor<Message> {

    private final VauClient vauClient;

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
            String path = vauPathHeaders.isEmpty() ? "undefined" : vauPathHeaders.getFirst();

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

            // hack for BareOutInterceptor not sending http request by xmlWriter.flush();
            message.put("org.apache.cxf.message.Message.ENCODING", "ISO 8859-1");

            OutputStream os = message.getContent(OutputStream.class);
            CachedStream cs = new CachedStream();
            message.setContent(OutputStream.class, cs);

            InterceptorChain interceptorChain = excludeInterceptors(
                message, SoapOutInterceptor.class, StaxOutInterceptor.class
            );
            interceptorChain.add(new VauStaxOutInterceptor());
            interceptorChain.add(new VauSoapOutInterceptor());
            interceptorChain.doIntercept(message);

            cs.flush();

            CachedOutputStream csNew = (CachedOutputStream) message.getContent(OutputStream.class);
            message.setContent(OutputStream.class, os);
            String payload = new String(csNew.getBytes(), UTF_8);
            payload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + payload;
            byte[] full = payload.getBytes();

            byte[] httpRequest = (path + " HTTP/1.1\r\n"
                + "Host: epa-as-1.dev.epa4all.de:443\r\n"
                + additionalHeaders + keepAlive
                + prepareContentHeaders(full)).getBytes();

            byte[] content = ArrayUtils.addAll(httpRequest, full);

            byte[] vauMessage = vauClient.getVauStateMachine().encryptVauMessage(content);

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

    private String prepareContentHeaders(byte[] originPayload) {
        int length = originPayload == null ? 0 :originPayload.length;
        return "Content-Type: text/xml; charset=utf-8\r\nContent-Length: " + length + "\r\n\r\n";
    }

    private class CachedStream extends CachedOutputStream {
        public CachedStream() {
            // forces CachedOutputStream to keep the whole content in-memory.
            super(1024 * 1024 * (long) 1024);
        }

        protected void doFlush() throws IOException {
            currentStream.flush();
        }

        protected void doClose() throws IOException {}

        protected void onWrite() throws IOException {}
    }
}
