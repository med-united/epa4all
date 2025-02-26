package de.servicehealth.epa4all.cxf;

import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static de.servicehealth.utils.ServerUtils.extractHeaders;
import static de.servicehealth.utils.ServerUtils.findHeader;
import static de.servicehealth.vau.VauClient.VAU_NON_PU_TRACING;
import static de.servicehealth.vau.VauClient.VAU_NP;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.HttpHeaders.HOST;
import static org.apache.cxf.helpers.HttpHeaderHelper.CONNECTION;

@SuppressWarnings("rawtypes")
public interface VauHeaders {

    default String evictHeader(Map<String, Object> httpHeaders, String headerName) {
        List headerList = (List) httpHeaders.remove(headerName);
        return headerList == null || headerList.isEmpty() ? null : String.valueOf(headerList.getFirst());
    }

    Map<String, String> backendMap = Map.of(
        "localhost:443", "localhost:443",
        "localhost:8071", "epa-as-1.dev.epa4all.de:443",
        "localhost:8072", "epa-as-2.dev.epa4all.de:443"
    );

    default List<Pair<String, String>> prepareInnerHeaders(
        Map<String, Object> httpHeaders,
        String backend,
        String vauNp
    ) {
        List<Pair<String, String>> headers = extractHeaders(httpHeaders, Set.of(CONTENT_TYPE, ACCEPT, VAU_NON_PU_TRACING));
        Optional<Pair<String, String>> headerOpt = findHeader(headers, CONNECTION);
        if (headerOpt.isEmpty()) {
            headers.add(Pair.of(CONNECTION, "Keep-Alive"));
        }
        headers.add(Pair.of(HOST, backend));
        if (vauNp != null) {
            headers.add(Pair.of(VAU_NP, vauNp));
        }
        return headers;
    }
}
