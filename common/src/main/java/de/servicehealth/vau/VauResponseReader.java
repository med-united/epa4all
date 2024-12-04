package de.servicehealth.vau;

import de.servicehealth.vau.response.CborVauErrorResponseBuilder;
import de.servicehealth.vau.response.DecryptedVauTextErrorResponseBuilder;
import de.servicehealth.vau.response.RawVauTextErrorResponseBuilder;
import de.servicehealth.vau.response.VauHeaderInnerResponseBuilder;
import de.servicehealth.vau.response.VauResponseBuilder;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class VauResponseReader {

    private final VauResponseBuilder vauResponseBuilder;

    public VauResponseReader(VauFacade vauFacade) {
        RawVauTextErrorResponseBuilder rawVauTextResponseBuilder = new RawVauTextErrorResponseBuilder();
        rawVauTextResponseBuilder
            .setNext(new CborVauErrorResponseBuilder(vauFacade))
            .setNext(new DecryptedVauTextErrorResponseBuilder())
            .setNext(new VauHeaderInnerResponseBuilder());

        vauResponseBuilder = rawVauTextResponseBuilder;
    }

    public VauResponse read(String vauCid, int responseCode, List<Pair<String, String>> headers, byte[] bytes) {
        return vauResponseBuilder.build(vauCid, responseCode, headers, bytes);
    }
}
