package de.servicehealth.epa4all.cxf.provider;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

import java.io.Serial;

import static de.servicehealth.utils.ServerUtils.extractJsonNode;

@Getter
public class VauException extends Exception {

    @Serial
    private static final long serialVersionUID = -7125370005700160802L;

    private final JsonNode jsonNode;

    public VauException(String message) {
        super(message);
        jsonNode = extractJsonNode(message);
    }
}