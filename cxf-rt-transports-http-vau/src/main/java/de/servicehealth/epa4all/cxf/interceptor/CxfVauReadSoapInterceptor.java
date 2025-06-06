package de.servicehealth.epa4all.cxf.interceptor;

import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauFacade;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.interceptor.AttachmentInInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.security.AuthenticationException;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import static de.servicehealth.utils.ServerUtils.isAuthError;
import static de.servicehealth.vau.VauClient.VAU_CID;
import static de.servicehealth.vau.VauFacade.SOAP_INVAL_AUTH;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.apache.cxf.message.Message.PROTOCOL_HEADERS;
import static org.apache.cxf.message.Message.RESPONSE_CODE;
import static org.apache.cxf.phase.Phase.RECEIVE;

public class CxfVauReadSoapInterceptor extends AbstractPhaseInterceptor<Message> {

    private static final Logger log = LoggerFactory.getLogger(CxfVauReadSoapInterceptor.class.getName());

    private final VauFacade vauFacade;

    public CxfVauReadSoapInterceptor(VauFacade vauFacade) {
        super(RECEIVE);
        addBefore(AttachmentInInterceptor.class.getName());
        this.vauFacade = vauFacade;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleMessage(Message message) throws Fault {
        boolean decrypted = false;
        String vauCid = (String) message.getExchange().get(VAU_CID);
        try {
            if (!message.get(RESPONSE_CODE).equals(200)) {
                String body = new String(message.getContent(InputStream.class).readAllBytes());
                throw new IllegalStateException(body);
            }
            VauClient vauClient = vauFacade.find(vauCid);
            if (vauClient == null) {
                String error = "Vau request read timed out, vauCid not found: " + vauCid;
                throw new IllegalStateException(error);
            }
            byte[] encryptedVauData = readContentFromMessage(message);
            byte[] decryptedBytes = vauClient.decryptVauMessage(encryptedVauData);
            decrypted = true;
            String fullRequest = new String(decryptedBytes, ISO_8859_1);

            message.put("org.apache.cxf.message.Message.ENCODING", Charset.defaultCharset().toString());

            Object headers = message.get(PROTOCOL_HEADERS);
            if (headers instanceof Map) {
                Map<String, List<String>> headersMap = (Map<String, List<String>>) headers;
                restoreHeaders(message, fullRequest, headersMap);
            }
            String body = fullRequest.substring(fullRequest.indexOf("\r\n\r\n") + 1);
            if (body.contains("RegistryError ") && body.contains(SOAP_INVAL_AUTH)) {
                throw new AuthenticationException(body);
            }
            InputStream is = new ByteArrayInputStream(body.getBytes(ISO_8859_1));
            message.setContent(InputStream.class, is);
        } catch (Exception e) {
            boolean noUserSession = isAuthError(e.getMessage());
            vauFacade.handleVauSessionError(vauCid, noUserSession, decrypted);
            throw new Fault(e);
        }
    }

    private void restoreHeaders(Message message, String fullRequest, Map<String, List<String>> headersMap) {
        Map<String, String> headerMap = new HashMap<>();
        String header = fullRequest.substring(0, fullRequest.indexOf("\r\n\r\n"));
        Arrays.stream(header.split("\r\n"))
            .filter(s -> s.contains(":"))
            .map(s -> s.split(":")).forEach(s -> headerMap.put(s[0], s[1].trim()));

        for (Entry<String, String> entry : headerMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(CONTENT_TYPE)) {
                message.put(CONTENT_TYPE, entry.getValue());
            }
            headersMap.put(entry.getKey().toLowerCase(), List.of(entry.getValue()));
        }
    }

    public byte[] readContentFromMessage(Message message) {
        InputStream is = message.getContent(InputStream.class);
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try {
            IOUtils.copy(is, bout);
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
        return bout.toByteArray();
    }
}
