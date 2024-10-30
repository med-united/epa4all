package de.servicehealth.epa4all.cxf.interceptor;

import static de.servicehealth.epa4all.cxf.interceptor.InterceptorUtils.excludeInterceptors;
import static de.servicehealth.epa4all.cxf.interceptor.InterceptorUtils.instanceOf;
import static de.servicehealth.epa4all.cxf.transport.HTTPClientVauConduit.VAU_METHOD_PATH;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.cxf.message.Message.PROTOCOL_HEADERS;
import static org.apache.cxf.phase.Phase.PRE_STREAM;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.InterceptorChain;
import org.apache.cxf.interceptor.StaxInInterceptor;
import org.apache.cxf.interceptor.StaxOutInterceptor;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.transport.http.Address;

import de.servicehealth.vau.VauClient;
import jakarta.ws.rs.core.HttpHeaders;

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

            Endpoint endpoint = message.getExchange().getEndpoint();
            List<Interceptor<? extends Message>> inInterceptors = endpoint.getBinding().getInInterceptors();
            Optional<Interceptor<? extends Message>> staxInInterceptorOpt = inInterceptors
                .stream()
                .filter(i -> instanceOf(i, StaxInInterceptor.class))
                .findFirst();
            staxInInterceptorOpt.ifPresent(inInterceptors::remove);

            
            InterceptorChain interceptorChain = excludeInterceptors(
                message/*, SoapOutInterceptor.class, StaxOutInterceptor.class, StaxInInterceptor.class*/
            );


            interceptorChain.doIntercept(message);

            cs.flush();

            CachedOutputStream csNew = (CachedOutputStream) message.getContent(OutputStream.class);
            message.setContent(OutputStream.class, os);
            String payload = new String(csNew.getBytes(), UTF_8);
            // payload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + payload;
            byte[] full = payload.getBytes();

            Address address = (Address) message.get("http.connection.address");
            String fullString = new String(full).replaceAll("http://schemas.xmlsoap.org/soap/envelope/", "http://www.w3.org/2003/05/soap-envelope");
            
			byte[] httpRequest = (path + " HTTP/1.1\r\n"
                + "Host: "+address.getURL().getHost()+"\r\n"
                + additionalHeaders + keepAlive
                + prepareContentHeaders(fullString.getBytes().length)).getBytes();

            byte[] content = ArrayUtils.addAll(httpRequest, fullString.getBytes());

            String soapMessageAsString = new String(content);
			
			log.info("Inner VAU Request:"+soapMessageAsString);
            
            byte[] vauMessage = vauClient.getVauStateMachine().encryptVauMessage(soapMessageAsString.getBytes());
            // httpHeaders.put(CONTENT_LENGTH, List.of(String.valueOf(vauMessage.length)));

            message.put("org.apache.cxf.message.Message.ENCODING", null);

            //if (os instanceof HTTPClientVauConduit.VauHttpClientWrappedOutputStream vwos) {
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

    private String prepareContentHeaders(int length) {
        return "Content-Type: application/soap+xml;charset=UTF-8;\r\nContent-Length: " + length + "\r\n\r\n";
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
