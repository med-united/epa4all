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
import org.apache.cxf.transport.http.Headers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.cxf.interceptor.InterceptorUtils.excludeInterceptors;
import static de.servicehealth.epa4all.cxf.transport.HTTPClientVauConduit.VAU_METHOD_PATH;
import static de.servicehealth.utils.ServerUtils.findHeader;
import static de.servicehealth.vau.VauClient.CLIENT_ID;
import static de.servicehealth.vau.VauClient.VAU_CID;
import static de.servicehealth.vau.VauClient.VAU_NON_PU_TRACING;
import static de.servicehealth.vau.VauClient.VAU_NP;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.HttpHeaders.HOST;
import static org.apache.cxf.message.Message.PROTOCOL_HEADERS;
import static org.apache.cxf.phase.Phase.PRE_STREAM;

public class CxfVauWriteSoapInterceptor extends AbstractPhaseInterceptor<Message> implements VauHeaders {

    private static final Logger log = Logger.getLogger(CxfVauWriteSoapInterceptor.class.getName());

    private final VauFacade vauFacade;

    public CxfVauWriteSoapInterceptor(VauFacade vauFacade) {
        super(PRE_STREAM);
        addBefore(LoggingOutInterceptor.class.getName());
        this.vauFacade = vauFacade;
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void handleMessage(Message message) throws Fault {
        try {
            TreeMap httpHeaders = (TreeMap) message.get(PROTOCOL_HEADERS);

            // 1. Headers manipulations
            String vauCid = evictHeader(httpHeaders, VAU_CID);
            if (!vauFacade.isTracingEnabled()) {
                evictHeader(httpHeaders, VAU_NON_PU_TRACING);
            }

            String methodWithPath = String.valueOf(message.get(VAU_METHOD_PATH));
            String statusLine = methodWithPath + " HTTP/1.1";

            // Getting xHeaders from the bindingProvider.getRequestContext
            String backend = String.valueOf(message.get(X_BACKEND));
            String xInsurantId = String.valueOf(message.get(X_INSURANT_ID));
            String xUserAgent = String.valueOf(message.get(X_USER_AGENT));
            String clientId = String.valueOf(message.get(CLIENT_ID));

            // DefaultLogEventMapper logs REQ_OUT before xHeaders from EpaContext are added
            // directly into HttpRequest.Builder rb,
            message.put(Headers.ADD_HEADERS_PROPERTY, true);
            httpHeaders.put(HOST, List.of(backend));
            httpHeaders.put(CLIENT_ID, List.of(clientId));
            httpHeaders.put(X_USER_AGENT, List.of(xUserAgent));
            httpHeaders.put(X_INSURANT_ID, List.of(xInsurantId));

            List<Pair<String, String>> headers = prepareHeaders(httpHeaders);
            if (findHeader(headers, VAU_NP).isEmpty()) {
                headers.add(Pair.of(VAU_NP, String.valueOf(message.get(VAU_NP))));
            }
            headers.add(Pair.of(CONTENT_TYPE, String.valueOf(message.get(CONTENT_TYPE))));

            // 2. Collecting payload, printing resulting headers
            OutputStream os = message.getContent(OutputStream.class);
            CachedStream cs = new CachedStream();
            byte[] payload = getPayload(os, cs, message);

            headers.add(Pair.of(CONTENT_LENGTH, String.valueOf(payload.length)));

            HttpParcel httpParcel = new HttpParcel(statusLine, headers, payload);
            log.info("SOAP Inner Request: " + httpParcel.toString(false, false));

            VauClient vauClient = vauFacade.getVauClient(vauCid);
            byte[] vauMessage = vauClient.getVauStateMachine().encryptVauMessage(httpParcel.toBytes());

            httpHeaders.put(CONTENT_LENGTH, List.of(String.valueOf(vauMessage.length)));

            message.put("org.apache.cxf.message.Message.ENCODING", null);

            try {
                os.write(vauMessage, 0, vauMessage.length);
            } finally {
                cs.close();
                os.flush();
                os.close();
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error while writing Vau SOAP message", e);
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
