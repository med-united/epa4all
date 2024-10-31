package de.servicehealth.epa4all.cxf.interceptor;

import static de.servicehealth.epa4all.cxf.interceptor.InterceptorUtils.excludeInterceptors;
import static de.servicehealth.epa4all.cxf.interceptor.InterceptorUtils.instanceOf;
import static de.servicehealth.epa4all.cxf.transport.HTTPClientVauConduit.VAU_METHOD_PATH;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.cxf.message.Message.PROTOCOL_HEADERS;
import static org.apache.cxf.phase.Phase.RECEIVE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.interceptor.AttachmentInInterceptor;
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

public class CxfVauReadSoapInterceptor extends AbstractPhaseInterceptor<Message> {

    private final VauClient vauClient;
    private static final Logger log = Logger.getLogger(CxfVauReadSoapInterceptor.class.getName());

    public CxfVauReadSoapInterceptor(VauClient vauClient) {
        super(RECEIVE);
        addBefore(AttachmentInInterceptor.class.getName());
        this.vauClient = vauClient;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleMessage(Message message) throws Fault {
        try {
            System.out.println(message);
            byte[] encryptedVauData = readContentFromMessage(message);
            byte[] decryptedBytes = vauClient.getVauStateMachine().decryptVauMessage(encryptedVauData);
            String fullRequest = new String(decryptedBytes);
			log.info("Inner Response: "+fullRequest);
            message.put("org.apache.cxf.message.Message.ENCODING", Charset.defaultCharset().toString());
            Map<String, String> headerMap = new HashMap<>();
            String header = fullRequest.substring(0, fullRequest.lastIndexOf("\r\n\r\n"));
            Arrays.stream(header.split("\r\n")).map(s -> s.split(":")).forEach(s -> headerMap.put(s[0], s[1].trim()));
            message.put("Content-Type", headerMap.get("Content-Type"));
            String body = fullRequest.substring(fullRequest.lastIndexOf("\r\n\r\n")+1);
            InputStream myInputStream = new ByteArrayInputStream(body.getBytes());
            message.setContent(InputStream.class, myInputStream);
        } catch (Exception e) {
            throw new Fault(e);
        }
    }
    
    public byte[] readContentFromMessage(Message message) {
        InputStream is = message.getContent(InputStream.class);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try {
          org.apache.cxf.helpers.IOUtils.copy(is, bout);
        } catch (IOException ex) {
          log.log(Level.SEVERE, ex.getMessage(), ex);
        }
        byte[] rawRequest = bout.toByteArray();
        return rawRequest;
      }

    private String prepareContentHeaders(int length) {
        return "Content-Type: application/soap+xml; charset=utf-8\r\nContent-Length: " + length + "\r\n\r\n";
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
