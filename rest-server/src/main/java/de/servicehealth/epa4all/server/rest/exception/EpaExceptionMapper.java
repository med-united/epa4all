package de.servicehealth.epa4all.server.rest.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class EpaExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger log = Logger.getLogger(EpaExceptionMapper.class.getName());

    @Override
    public Response toResponse(Exception exception) {
        log.log(Level.SEVERE, "Client EXCEPTION", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(new EpaClientError(exception.getMessage()))
            .type(MediaType.APPLICATION_JSON)
            .build();
    }
}
