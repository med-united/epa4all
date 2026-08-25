package de.servicehealth.epa4all.cxf.provider;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

import java.io.Serial;

import static de.servicehealth.utils.ServerUtils.extractJsonNode;

@Getter
public class VauException extends Exception {

    @Serial
    private static final long serialVersionUID = -7125370005700160802L;

    public static final int DEFAULT_ERROR_STATUS = 409;

    private final JsonNode jsonNode;
    private final int status;

    public VauException(String message) {
        this(message, DEFAULT_ERROR_STATUS);
    }

    public VauException(String message, int status) {
        super(message);
        jsonNode = extractJsonNode(message);
        this.status = status;
    }
}