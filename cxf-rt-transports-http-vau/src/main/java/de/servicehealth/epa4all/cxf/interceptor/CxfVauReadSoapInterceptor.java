package de.servicehealth.epa4all.cxf.interceptor;

import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauFacade;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.interceptor.AttachmentInInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.servicehealth.vau.VauClient.VAU_CID;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static org.apache.cxf.phase.Phase.RECEIVE;

public class CxfVauReadSoapInterceptor extends AbstractPhaseInterceptor<Message> {

    private final VauFacade vauFacade;
    private static final Logger log = Logger.getLogger(CxfVauReadSoapInterceptor.class.getName());

    public CxfVauReadSoapInterceptor(VauFacade vauFacade) {
        super(RECEIVE);
        addBefore(AttachmentInInterceptor.class.getName());
        this.vauFacade = vauFacade;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleMessage(Message message) throws Fault {
        try {
            if (!message.get(Message.RESPONSE_CODE).equals(200)) {
                String body = new String(message.getContent(InputStream.class).readAllBytes());
                throw new Fault(body, log);
            }
            byte[] encryptedVauData = readContentFromMessage(message);
            String vauCid = (String) message.getExchange().get(VAU_CID);
            VauClient vauClient = vauFacade.getVauClient(vauCid);
            byte[] decryptedBytes = vauClient.decryptVauMessage(encryptedVauData);
            String fullRequest = new String(decryptedBytes);
            message.put("org.apache.cxf.message.Message.ENCODING", Charset.defaultCharset().toString());
            Map<String, String> headerMap = new HashMap<>();
            String header = fullRequest.substring(0, fullRequest.indexOf("\r\n\r\n"));
            Arrays.stream(header.split("\r\n"))
                .filter(s -> s.contains(":"))
                .map(s -> s.split(":")).forEach(s -> headerMap.put(s[0], s[1].trim()));
            Object headers = message.get(org.apache.cxf.message.Message.PROTOCOL_HEADERS);
            if (headers instanceof Map) {
                Map<String, List<String>> headersMap = (Map<String, List<String>>) headers;
                for (Entry<String, String> entry : headerMap.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(CONTENT_TYPE)) {
                        message.put(Message.CONTENT_TYPE, entry.getValue());
                    }
                    headersMap.put(entry.getKey().toLowerCase(), List.of(entry.getValue()));
                }
            }
            String body = fullRequest.substring(fullRequest.indexOf("\r\n\r\n") + 1);
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
            IOUtils.copy(is, bout);
        } catch (IOException ex) {
            log.log(Level.SEVERE, ex.getMessage(), ex);
        }
        return bout.toByteArray();
    }
}
