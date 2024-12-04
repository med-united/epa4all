package de.servicehealth.vau.response;

import de.servicehealth.vau.VauResponse;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public abstract class AbstractVauResponseBuilder implements VauResponseBuilder {

    protected Logger log = LoggerFactory.getLogger(getClass());
    
    private VauResponseBuilder next;

    @Override
    public VauResponseBuilder setNext(VauResponseBuilder builder) {
        next = builder;
        return builder;
    }

    @Override
    public VauResponse build(String vauCid, int responseCode, List<Pair<String, String>> headers, byte[] bytes) {
        if (next != null) {
            return next.build(vauCid, responseCode, headers, bytes);
        } else {
            throw new IllegalStateException("No final VauResponse builder is found");
        }
    }

    protected Optional<String> findHeaderValue(List<Pair<String, String>> headers, String headerName) {
        return headers.stream()
            .filter(p -> p.getKey().equalsIgnoreCase(headerName))
            .map(Pair::getValue)
            .findFirst();
    }
}
