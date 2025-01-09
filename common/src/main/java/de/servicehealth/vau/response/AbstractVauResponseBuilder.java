package de.servicehealth.vau.response;

import de.servicehealth.vau.VauResponse;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.logging.Logger;

public abstract class AbstractVauResponseBuilder implements VauResponseBuilder {

    private final Logger log = Logger.getLogger(getClass().getName());
    
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
}
