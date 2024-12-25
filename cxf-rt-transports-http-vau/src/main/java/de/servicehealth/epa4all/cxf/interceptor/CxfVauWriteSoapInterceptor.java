package de.servicehealth.epa4all.cxf.interceptor;

import de.servicehealth.epa4all.cxf.VauHeaders;
import de.servicehealth.http.HttpParcel;
import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauFacade;
import org.apache.commons.lang3.tuple.Pair;
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
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.cxf.interceptor.InterceptorUtils.excludeInterceptors;
import static de.servicehealth.vau.VauClient.VAU_CID;
import static de.servicehealth.vau.VauClient.VAU_NON_PU_TRACING;
import static de.servicehealth.vau.VauClient.VAU_NP;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static de.servicehealth.vau.VauClient.X_USER_AGENT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static org.apache.cxf.message.Message.PROTOCOL_HEADERS;
import static org.apache.cxf.phase.Phase.PRE_STREAM;

public class CxfVauWriteSoapInterceptor extends AbstractPhaseInterceptor<Message> implements VauHeaders {

    private static final Logger log = Logger.getLogger(CxfVauWriteSoapInterceptor.class.getName());

    private final VauFacade vauFacade;

    public CxfVauWriteSoapInterceptor(VauFacade vauFacade) {
        super(PRE_STREAM);
        addBefore(StaxOutInterceptor.class.getName());
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

            String vauCid = evictHeader(httpHeaders, VAU_CID);
            if (!vauFacade.isTracingEnabled()) {
                evictHeader(httpHeaders, VAU_NON_PU_TRACING);
            }

            OutputStream os = message.getContent(OutputStream.class);
            CachedStream cs = new CachedStream();
            byte[] payload = getPayload(os, cs, message);
            String statusLine = getStatusLine(null, httpHeaders);

            List<Pair<String, String>> headers = prepareHeaders(httpHeaders);

            headers.add(Pair.of(X_BACKEND, String.valueOf(message.get(X_BACKEND))));
            headers.add(Pair.of(X_INSURANT_ID, String.valueOf(message.get(X_INSURANT_ID))));
            headers.add(Pair.of(X_USER_AGENT, String.valueOf(message.get(X_USER_AGENT))));
            headers.add(Pair.of(VAU_NP, String.valueOf(message.get(VAU_NP))));
            headers.add(Pair.of(CONTENT_TYPE, String.valueOf(message.get(CONTENT_TYPE))));
            headers.add(Pair.of(CONTENT_LENGTH, String.valueOf(payload.length)));

            HttpParcel httpParcel = new HttpParcel(statusLine, headers, payload);

            VauClient vauClient = vauFacade.getVauClient(vauCid);
            byte[] vauMessage = vauClient.getVauStateMachine().encryptVauMessage(httpParcel.toBytes());

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

    private static class CachedStream extends CachedOutputStream {
        public CachedStream() {
            // forces CachedOutputStream to keep the whole content in-memory.
            super(1024 * 1024 * (long) 1024);
        }

        protected void doFlush() throws IOException {
            currentStream.flush();
        }
    }
}
