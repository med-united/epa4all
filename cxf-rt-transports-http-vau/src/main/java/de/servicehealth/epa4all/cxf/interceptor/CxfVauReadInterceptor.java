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
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

public class CxfVauReadInterceptor extends AbstractPhaseInterceptor<Message> {

    private final VauResponseReader vauResponseReader;

    public CxfVauReadInterceptor(VauClient vauClient) {
        super(Phase.PROTOCOL);
        vauResponseReader = new VauResponseReader(vauClient);;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleMessage(Message message) throws Fault {
        try {
            InputStream inputStream = message.getContent(InputStream.class);
            VauResponse vauResponse = vauResponseReader.read(inputStream.readAllBytes());
            List<Pair<String, String>> headers = vauResponse.headers();
            Optional<Pair<String, String>> locationOpt = headers.stream()
                .filter(p -> p.getKey().equals(HttpHeaders.LOCATION))
                .findFirst();

            locationOpt.ifPresent(p -> {
                String protocolHeaders = "org.apache.cxf.message.Message.PROTOCOL_HEADERS";
                TreeMap<String, Object> map = (TreeMap<String, Object>) message.get(protocolHeaders);
                map.put(HttpHeaders.LOCATION, List.of(p.getValue()));
            });

            byte[] payload = vauResponse.payload();
            if (payload != null) {
                message.setContent(InputStream.class, new ByteArrayInputStream(payload));
            }
        } catch (IOException e) {
            throw new Fault(e);
        }
    }
}
