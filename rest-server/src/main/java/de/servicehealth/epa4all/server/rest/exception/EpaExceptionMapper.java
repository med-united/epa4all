package de.servicehealth.epa4all.server.rest.exception;

import com.fasterxml.jackson.databind.JsonNode;
import de.servicehealth.epa4all.cxf.provider.VauException;
import io.quarkus.security.AuthenticationFailedException;
import jakarta.ws.rs.client.ResponseProcessingException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;

@Provider
public class EpaExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger log = LoggerFactory.getLogger(EpaExceptionMapper.class.getName());

    private static class ExInfo {
        private final String message;
        private final JsonNode jsonNode;
        private final Response.Status status;

        public ExInfo(String message, JsonNode jsonNode, Response.Status status) {
            this.message = message;
            this.jsonNode = jsonNode;
            this.status = status;
        }
    }

    private ExInfo extractPossibleVauException(Throwable t) {
        if (t instanceof VauException vauException) {
            return new ExInfo(null, vauException.getJsonNode(), CONFLICT);
        } else {
            return new ExInfo(t.getMessage(), null, INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Response toResponse(Exception exception) {
        log.error("Client EXCEPTION", exception);

        ExInfo exInfo = switch (exception) {
            case ResponseProcessingException processingException -> extractPossibleVauException(getOriginalCause(processingException));
            case AuthenticationFailedException authException -> new ExInfo(authException.getMessage(), null, UNAUTHORIZED);
            case null -> new ExInfo("Unknown error", null, INTERNAL_SERVER_ERROR);
            default -> extractPossibleVauException(getOriginalCause(exception));
        };
        return Response.status(exInfo.status)
            .entity(exInfo.jsonNode == null ? new EpaClientError(exInfo.message) : exInfo.jsonNode)
            .type(APPLICATION_JSON)
            .build();
    }

    private Throwable getOriginalCause(Exception exception) {
        Throwable cause = exception.getCause();
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}