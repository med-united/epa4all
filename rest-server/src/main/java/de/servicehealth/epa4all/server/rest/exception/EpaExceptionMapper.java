package de.servicehealth.epa4all.server.rest.exception;

import com.fasterxml.jackson.databind.JsonNode;
import de.servicehealth.api.epa4all.EpaNotFoundException;
import de.servicehealth.epa4all.cxf.provider.VauException;
import de.servicehealth.epa4all.server.pnw.ConsentException;
import de.servicehealth.epa4all.server.pnw.PnwException;
import de.servicehealth.epa4all.server.pnw.PnwResponse;
import de.servicehealth.epa4all.server.tss.TssException;
import io.quarkus.security.AuthenticationFailedException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static de.servicehealth.utils.ServerUtils.getOriginalCause;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;

@Provider
public class EpaExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger log = LoggerFactory.getLogger(EpaExceptionMapper.class.getName());

    private static class ErrorStatus {
        private final String message;
        private JsonNode jsonNode;
        private Object xmlRoot;
        private final Response.Status status;

        public ErrorStatus(String message, JsonNode jsonNode, Object xmlRoot, Response.Status status) {
            this.message = message;
            this.jsonNode = jsonNode;
            this.xmlRoot = xmlRoot;
            this.status = status;
        }

        public ErrorStatus(String message, Response.Status status) {
            this.message = message;
            this.status = status;
        }
    }

    private ErrorStatus extractException(Throwable t) {
        if (t instanceof VauException vauException) {
            return new ErrorStatus(null, vauException.getJsonNode(), null, CONFLICT);
        } else if (t instanceof PnwException pnwException) {
            PnwResponse pnwResponse = new PnwResponse(
                pnwException.getKvnr(),
                null,
                null,
                null,
                pnwException.getMessage()
            );
            return new ErrorStatus(null, null, pnwResponse, CONFLICT);
        } else {
            return new ErrorStatus(t.getMessage(), INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Response toResponse(Exception exception) {
        log.error("Client EXCEPTION", getOriginalCause(exception));

        ErrorStatus errorStatus = switch (exception) {
            case AuthenticationFailedException authEx -> new ErrorStatus(authEx.getMessage(), UNAUTHORIZED);
            case EpaNotFoundException epaEx -> new ErrorStatus(epaEx.getMessage(), NOT_FOUND);
            case ConsentException consentEx -> new ErrorStatus(consentEx.getMessage(), FORBIDDEN);
            case TssException tssEx -> new ErrorStatus(tssEx.getMessage(), tssEx.getStatus());
            case BadRequestException badRequestEx -> new ErrorStatus(badRequestEx.getMessage(), BAD_REQUEST);
            case XdsException xdsException -> new ErrorStatus(null, null, xdsException.getRegistryResponse(), CONFLICT);
            case null -> new ErrorStatus("Unknown error", INTERNAL_SERVER_ERROR);
            default -> extractException(getOriginalCause(exception));
        };
        Object entity = errorStatus.message != null
            ? new EpaClientError(errorStatus.message)
            : errorStatus.jsonNode != null ? errorStatus.jsonNode : errorStatus.xmlRoot;

        String type = errorStatus.xmlRoot != null ? APPLICATION_XML : APPLICATION_JSON;
        return Response.status(errorStatus.status).entity(entity).type(type).build();
    }
}