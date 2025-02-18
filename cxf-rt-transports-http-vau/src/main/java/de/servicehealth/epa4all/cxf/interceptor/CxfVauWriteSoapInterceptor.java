package de.servicehealth.epa4all.cxf.interceptor;

import de.servicehealth.epa4all.cxf.VauHeaders;
import de.servicehealth.http.HttpParcel;
import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauFacade;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.InterceptorChain;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.transport.http.HTTPException;
import org.apache.cxf.transport.http.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import static de.servicehealth.epa4all.cxf.interceptor.InterceptorUtils.excludeInterceptors;
import static de.servicehealth.epa4all.cxf.transport.HTTPClientVauConduit.VAU_METHOD_PATH;
import static de.servicehealth.utils.ServerUtils.isAuthError;
import static de.servicehealth.vau.VauClient.CLIENT_ID;
import static de.servicehealth.vau.VauClient.TASK_ID;
import static de.servicehealth.vau.VauClient.VAU_CID;
import static de.servicehealth.vau.VauClient.VAU_CLIENT_UUID;
import static de.servicehealth.vau.VauClient.VAU_NP;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;
import static de.servicehealth.vau.VauClient.X_WORKPLACE;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static org.apache.cxf.message.Message.PROTOCOL_HEADERS;
import static org.apache.cxf.phase.Phase.PRE_STREAM;

@SuppressWarnings({"unchecked", "rawtypes"})
public class CxfVauWriteSoapInterceptor extends AbstractPhaseInterceptor<Message> implements VauHeaders {

    private static final Logger log = LoggerFactory.getLogger(CxfVauWriteSoapInterceptor.class.getName());

    private final VauFacade vauFacade;
    private final Set<String> maskedHeaders;
    private final Set<String> maskedAttributes;

    public CxfVauWriteSoapInterceptor(VauFacade vauFacade, Set<String> maskedHeaders, Set<String> maskedAttributes) {
        super(PRE_STREAM);
        addBefore(LoggingOutInterceptor.class.getName());
        this.vauFacade = vauFacade;
        this.maskedHeaders = new HashSet<>(maskedHeaders);
        this.maskedAttributes = new HashSet<>(maskedAttributes);
    }

    private byte[] getPayload(OutputStream os, CachedStream cs, Message message) throws Exception {
        message.setContent(OutputStream.class, cs);
        InterceptorChain interceptorChain = excludeInterceptors(message);
        interceptorChain.doIntercept(message);
        cs.flush();
        CachedOutputStream csNew = (CachedOutputStream) message.getContent(OutputStream.class);
        message.setContent(OutputStream.class, os);
        return csNew.getBytes();
    }

    private void addOuterHeader(Message message, TreeMap httpHeaders, String headerName) {
        Object value = message.get(headerName);
        if (value != null) {
            httpHeaders.put(headerName, List.of(String.valueOf(value)));
        }
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        boolean encrypted = false;
        String vauCid = (String) message.getExchange().get(VAU_CID);
        try {
            TreeMap httpHeaders = (TreeMap) message.get(PROTOCOL_HEADERS);

            /*1. Headers manipulations*/
            evictHeader(httpHeaders, VAU_CLIENT_UUID);
            evictHeader(httpHeaders, VAU_METHOD_PATH);
            evictHeader(httpHeaders, X_BACKEND);
            evictHeader(httpHeaders, X_KONNEKTOR);
            evictHeader(httpHeaders, X_WORKPLACE);
            evictHeader(httpHeaders, X_INSURANT_ID);

            vauCid = evictHeader(httpHeaders, VAU_CID);

            // Getting xHeaders provided by bindingProvider.getRequestContext
            String backend = String.valueOf(message.get(X_BACKEND));
            String vauNp = vauFacade.find(vauCid).getVauNp();

            // All outer headers must be set before step 3 so DefaultLogEventMapper will log them in REQ_OUT
            message.put(Headers.ADD_HEADERS_PROPERTY, true);
            addOuterHeader(message, httpHeaders, VAU_NP);
            addOuterHeader(message, httpHeaders, CLIENT_ID);
            addOuterHeader(message, httpHeaders, X_USER_AGENT);
            addOuterHeader(message, httpHeaders, X_INSURANT_ID);
            addOuterHeader(message, httpHeaders, TASK_ID);

            /*2. Mirror Message.PROTOCOL_HEADERS into inner HttpRequest headers*/
            List<Pair<String, String>> innerHeaders = prepareInnerHeaders(httpHeaders, backend, vauNp);

            httpHeaders.remove(VAU_NP);
            httpHeaders.remove(X_INSURANT_ID);

            /*3. Collecting payload, printing resulting outer headers*/
            OutputStream os = message.getContent(OutputStream.class);
            CachedStream cs = new CachedStream();
            byte[] payload = getPayload(os, cs, message);

            // ContentType is fully constructed after payload is built
            innerHeaders.add(Pair.of(CONTENT_TYPE, String.valueOf(message.get(CONTENT_TYPE))));
            innerHeaders.add(Pair.of(CONTENT_LENGTH, String.valueOf(payload.length)));

            String methodWithPath = String.valueOf(message.get(VAU_METHOD_PATH));
            String statusLine = methodWithPath + " HTTP/1.1";
            HttpParcel httpParcel = new HttpParcel(statusLine, innerHeaders, payload);

            String requestAsString = httpParcel.toString(false, false, maskedHeaders, maskedAttributes);
            log.info(String.format("SOAP Inner Request: %s", requestAsString));

            httpHeaders.remove(TASK_ID);
            httpParcel.getHeaders().stream().filter(p -> p.getKey().equals(TASK_ID)).findFirst().ifPresent(p ->
                httpParcel.getHeaders().remove(p)
            );

            /*4. Prepare Vau message*/
            byte[] vauMessage;
            try {
                VauClient vauClient = vauFacade.find(vauCid);
                vauMessage = vauClient.encryptVauMessage(httpParcel.toBytes());
            } finally {
                encrypted = true;
            }

            httpHeaders.put(CONTENT_LENGTH, List.of(String.valueOf(vauMessage.length)));

            // confirm
            message.put("org.apache.cxf.message.Message.ENCODING", null);

            try {
                os.write(vauMessage, 0, vauMessage.length);
            } finally {
                cs.close();
                os.flush();
                os.close();
            }
        } catch (Exception e) {
            log.error("Error while sending Vau SOAP message", e);
            if (encrypted || e instanceof HTTPException) {
                boolean noUserSession = isAuthError(e.getMessage());
                vauFacade.handleVauSessionError(vauCid, noUserSession, false);
            }
            throw new Fault(e);
        }
    }

    @SuppressWarnings("InnerClassMayBeStatic")
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
