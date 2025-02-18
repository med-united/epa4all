package de.servicehealth.epa4all.cxf.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

@Getter
@AllArgsConstructor
public class ForwardRequest implements EpaRequest {

    private final boolean isGet;
    private final List<Pair<String, String>> acceptHeaders;
    private final List<Pair<String, String>> contentHeaders;
    private final byte[] body;

    public String getMethod() {
        return isGet ? "GET" : "POST";
    }
}
