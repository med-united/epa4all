package de.servicehealth.epa4all.cxf.interceptor;

import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauResponse;
import de.servicehealth.vau.VauResponseReader;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.cxf.interceptor.InterceptorUtils.addProtocolHeader;
import static de.servicehealth.epa4all.cxf.interceptor.InterceptorUtils.getProtocolHeaders;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.LOCATION;
import static org.apache.cxf.message.Message.RESPONSE_CODE;

public class CxfVauReadInterceptor extends AbstractPhaseInterceptor<Message> {

    public static final String VAU_ERROR = "VAU_ERROR";

    private final VauResponseReader vauResponseReader;
    
    private static Logger log = Logger.getLogger(CxfVauReadInterceptor.class.getName());

    public CxfVauReadInterceptor(VauClient vauClient) {
        super(Phase.PROTOCOL);
        vauResponseReader = new VauResponseReader(vauClient);
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        try {
            InputStream inputStream = message.getContent(InputStream.class);
            Integer responseCode = (Integer) message.get(RESPONSE_CODE);
            VauResponse vauResponse = vauResponseReader.read(
                responseCode, getProtocolHeaders(message), inputStream.readAllBytes()
            );
            List<Pair<String, String>> headers = vauResponse.headers();
            Optional<Pair<String, String>> locationOpt = headers.stream()
                .filter(p -> p.getKey().equals(LOCATION))
                .findFirst();

            locationOpt.ifPresent(p -> addProtocolHeader(message, LOCATION, p.getValue()));
            if (vauResponse.generalError() != null) {
                addProtocolHeader(message, VAU_ERROR, vauResponse.generalError());
            }
            byte[] payload = vauResponse.payload();
            if (payload != null) {
            	log.info("Response: "+new String(payload));
                message.setContent(InputStream.class, new ByteArrayInputStream(payload));
                addProtocolHeader(message, CONTENT_LENGTH, payload.length);
            }
        } catch (Exception e) {
            throw new Fault(e);
        }
    }
}
