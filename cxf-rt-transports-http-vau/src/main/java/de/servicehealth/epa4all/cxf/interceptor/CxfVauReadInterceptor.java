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
import java.util.TreeMap;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.LOCATION;
import static org.apache.cxf.message.Message.PROTOCOL_HEADERS;
import static org.apache.cxf.message.Message.RESPONSE_CODE;

@SuppressWarnings("unchecked")
public class CxfVauReadInterceptor extends AbstractPhaseInterceptor<Message> {

    public static final String VAU_ERROR = "VAU_ERROR";

    private final VauResponseReader vauResponseReader;

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
                message.setContent(InputStream.class, new ByteArrayInputStream(payload));
                addProtocolHeader(message, CONTENT_LENGTH, payload.length);
            }
        } catch (Exception e) {
            throw new Fault(e);
        }
    }

    private void addProtocolHeader(Message message, String name, Object value) {
        TreeMap<String, Object> map = (TreeMap<String, Object>) message.get(PROTOCOL_HEADERS);
        map.put(name, List.of(value));
    }

    private List<Pair<String, String>> getProtocolHeaders(Message message) {
        TreeMap<String, Object> map = (TreeMap<String, Object>) message.get(PROTOCOL_HEADERS);
        return map.entrySet().stream()
            .map(e -> Pair.of(e.getKey(), ((List<String>) e.getValue()).getFirst()))
            .toList();
    }
}
