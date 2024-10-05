package de.servicehealth.epa4all.cxf.interceptor;

import de.servicehealth.epa4all.VauClient;
import de.servicehealth.epa4all.VauResponse;
import de.servicehealth.epa4all.VauResponseReader;
import jakarta.ws.rs.core.HttpHeaders;
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

public class CxfVauReadInterceptor extends AbstractPhaseInterceptor<Message> {

    public static final String VAU_ERROR = "VAU_ERROR";
    public static final String PROTOCOL_HEADERS_KEY = "org.apache.cxf.message.Message.PROTOCOL_HEADERS";

    private final VauResponseReader vauResponseReader;

    public CxfVauReadInterceptor(VauClient vauClient) {
        super(Phase.PROTOCOL);
        vauResponseReader = new VauResponseReader(vauClient);;
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        try {
            InputStream inputStream = message.getContent(InputStream.class);
            Integer responseCode = (Integer) message.get("org.apache.cxf.message.Message.RESPONSE_CODE");
            VauResponse vauResponse = vauResponseReader.read(
                responseCode, getProtocolHeaders(message), inputStream.readAllBytes()
            );
            List<Pair<String, String>> headers = vauResponse.headers();
            Optional<Pair<String, String>> locationOpt = headers.stream()
                .filter(p -> p.getKey().equals(HttpHeaders.LOCATION))
                .findFirst();

            locationOpt.ifPresent(p -> addProtocolHeader(message, HttpHeaders.LOCATION, p.getValue()));
            addProtocolHeader(message, VAU_ERROR, vauResponse.generalError());
            byte[] payload = vauResponse.payload();
            if (payload != null) {
                message.setContent(InputStream.class, new ByteArrayInputStream(payload));
            }
        } catch (Exception e) {
            throw new Fault(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void addProtocolHeader(Message message, String name, Object value) {
        TreeMap<String, Object> map = (TreeMap<String, Object>) message.get(PROTOCOL_HEADERS_KEY);
        map.put(name, List.of(value));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String getProtocolHeader(Message message, String name) {
        TreeMap<String, Object> map = (TreeMap<String, Object>) message.get(PROTOCOL_HEADERS_KEY);
        List list = (List) map.get(name);
        return list != null && !list.isEmpty() ? (String) list.getFirst() : null;
    }

    @SuppressWarnings("unchecked")
    private List<Pair<String, String>> getProtocolHeaders(Message message) {
        TreeMap<String, Object> map = (TreeMap<String, Object>) message.get(PROTOCOL_HEADERS_KEY);
        return map.entrySet().stream()
            .map(e -> Pair.of(e.getKey(), ((List<String>) e.getValue()).getFirst()))
            .toList();
    }
}
