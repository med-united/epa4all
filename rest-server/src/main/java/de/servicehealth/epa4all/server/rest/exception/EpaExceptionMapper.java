package de.servicehealth.epa4all.server.rest.exception;

import io.quarkus.security.AuthenticationFailedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;

@Provider
public class EpaExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger log = LoggerFactory.getLogger(EpaExceptionMapper.class.getName());

    @Override
    public Response toResponse(Exception exception) {
        log.error("Client EXCEPTION", exception);
        Response.Status status = exception instanceof AuthenticationFailedException
            ? UNAUTHORIZED
            : INTERNAL_SERVER_ERROR;
        return Response.status(status)
            .entity(new EpaClientError(exception.getMessage()))
            .type(APPLICATION_JSON)
            .build();
    }
}
