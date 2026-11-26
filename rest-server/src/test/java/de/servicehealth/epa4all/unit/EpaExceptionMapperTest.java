package de.servicehealth.epa4all.unit;

import com.fasterxml.jackson.databind.JsonNode;
import de.servicehealth.epa4all.cxf.provider.VauException;
import de.servicehealth.epa4all.server.rest.exception.EpaExceptionMapper;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class EpaExceptionMapperTest {

    private static final String XML_ERROR = "<OperationOutcome xmlns=\"http://hl7.org/fhir\"/>";
    private static final String JSON_ERROR = "{\"resourceType\":\"OperationOutcome\"}";

    private final EpaExceptionMapper mapper = new EpaExceptionMapper();

    @Test
    void backendXmlErrorIsReturnedUnchanged() {
        VauException backendError = new VauException(XML_ERROR, 400, "application/fhir+xml");

        try (Response response = mapper.toResponse(new IOException(backendError))) {
            assertEquals(400, response.getStatus());
            assertEquals(MediaType.valueOf("application/fhir+xml"), response.getMediaType());
            assertEquals(XML_ERROR, response.getEntity());
        }
    }

    @Test
    void backendJsonErrorIsReturnedUnchanged() {
        VauException backendError = new VauException(JSON_ERROR, 422, "application/fhir+json");

        try (Response response = mapper.toResponse(new IOException(backendError))) {
            assertEquals(422, response.getStatus());
            assertEquals(MediaType.valueOf("application/fhir+json"), response.getMediaType());
            assertEquals(JSON_ERROR, response.getEntity());
        }
    }

    @Test
    void errorWithoutContentTypeStaysJson() {
        try (Response response = mapper.toResponse(new IOException(new VauException(JSON_ERROR, 409)))) {
            assertEquals(409, response.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
            assertInstanceOf(JsonNode.class, response.getEntity());
        }
    }
}
