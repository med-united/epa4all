package de.servicehealth.vau.response;

import de.servicehealth.vau.VauResponse;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public interface VauResponseBuilder {

    VauResponseBuilder setNext(VauResponseBuilder builder);

    VauResponse build(String vauCid, int responseCode, List<Pair<String, String>> headers, byte[] bytes);
}
