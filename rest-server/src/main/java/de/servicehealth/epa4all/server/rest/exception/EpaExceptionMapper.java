package de.servicehealth.epa4all.server.rest.exception;

import com.fasterxml.jackson.databind.JsonNode;
import de.servicehealth.epa4all.cxf.provider.VauException;
import de.servicehealth.epa4all.server.pnw.PnwException;
import de.servicehealth.epa4all.server.pnw.PnwResponse;
import io.quarkus.security.AuthenticationFailedException;
import jakarta.ws.rs.client.ResponseProcessingException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static de.servicehealth.utils.ServerUtils.getOriginalCause;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;
import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;

@Provider
public class EpaExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger log = LoggerFactory.getLogger(EpaExceptionMapper.class.getName());

    private static class ExInfo {
        private final String message;
        private final JsonNode jsonNode;
        private final Object xmlRoot;
        private final Response.Status status;

        public ExInfo(String message, JsonNode jsonNode, Object xmlRoot, Response.Status status) {
            this.message = message;
            this.jsonNode = jsonNode;
            this.xmlRoot = xmlRoot;
            this.status = status;
        }
    }

    private ExInfo extractPossibleVauException(Throwable t) {
        if (t instanceof VauException vauException) {
            return new ExInfo(null, vauException.getJsonNode(), null, CONFLICT);
        } else if (t instanceof PnwException pnwException) {
            PnwResponse pnwResponse = new PnwResponse(
                pnwException.getKvnr(),
                null,
                null,
                pnwException.getMessage()
            );
            return new ExInfo(null, null, pnwResponse, CONFLICT);
        } else {
            return new ExInfo(t.getMessage(), null, null, INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Response toResponse(Exception exception) {
        log.error("Client EXCEPTION", getOriginalCause(exception));

        ExInfo exInfo = switch (exception) {
            case ResponseProcessingException processingException -> extractPossibleVauException(getOriginalCause(processingException));
            case AuthenticationFailedException authException -> new ExInfo(authException.getMessage(), null, null, UNAUTHORIZED);
            case PnwException pnwException -> extractPossibleVauException(getOriginalCause(pnwException));
            case null -> new ExInfo("Unknown error", null, null, INTERNAL_SERVER_ERROR);
            default -> extractPossibleVauException(getOriginalCause(exception));
        };
        Object entity = exInfo.message != null
            ? new EpaClientError(exInfo.message)
            : exInfo.jsonNode != null ? exInfo.jsonNode : exInfo.xmlRoot;

        String type = exInfo.xmlRoot != null ? APPLICATION_XML : APPLICATION_JSON;
        return Response.status(exInfo.status).entity(entity).type(type).build();
    }
}