package de.servicehealth.epa4all.cxf;

import de.servicehealth.epa4all.cxf.model.FhirRequest;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.servicehealth.epa4all.cxf.transport.HTTPClientVauConduit.VAU_METHOD_PATH;
import static de.servicehealth.utils.ServerUtils.findHeaderValue;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.HttpHeaders.HOST;
import static org.apache.cxf.helpers.HttpHeaderHelper.CONNECTION;

@SuppressWarnings("rawtypes")
public interface VauHeaders {

    default String getStatusLine(Object obj, Map<String, Object> httpHeaders) {
        String vmp = evictHeader(httpHeaders, VAU_METHOD_PATH);
        if (vmp != null && obj instanceof FhirRequest fhirRequest) {
            String method = vmp.trim().split(" ")[0];
            vmp = vmp.replace(method, fhirRequest.getMethod());
        }
        return vmp + " HTTP/1.1";
    }

    default String evictHeader(Map<String, Object> httpHeaders, String headerName) {
        List headerList = (List) httpHeaders.remove(headerName);
        return headerList == null || headerList.isEmpty() ? null : String.valueOf(headerList.getFirst());
    }

    default List<Pair<String, String>> extractHeaders(Map<String, Object> httpHeaders, Set<String> excluded) {
        return new ArrayList<>(httpHeaders.entrySet()
            .stream()
            .filter(p -> excluded.isEmpty() || excluded.stream().noneMatch(ex -> p.getKey().equalsIgnoreCase(ex)))
            .map(p -> Pair.of(p.getKey(), String.valueOf(((List) p.getValue()).getFirst())))
            .toList());
    }

    default List<Pair<String, String>> prepareHeaders(Map<String, Object> httpHeaders) {
        List<Pair<String, String>> headers = extractHeaders(httpHeaders, Set.of(CONTENT_TYPE, ACCEPT));
        findHeaderValue(headers, CONNECTION).ifPresent(s -> {
            if (!s.contains("Keep-Alive")) {
                headers.add(Pair.of(CONNECTION, "Keep-Alive"));
            }
        });
        return headers;
    }
}
