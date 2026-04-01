package de.servicehealth.epa4all.server.rest.fhir;

import ca.uhn.fhir.context.FhirContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.Provider;
import org.hl7.fhir.r4.model.Bundle;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
@Consumes("application/fhir+json")
public class FhirJsonMessageReader implements MessageBodyReader<Bundle> {

    private static final FhirContext FHIR_CTX = FhirContext.forR4();

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return type == Bundle.class;
    }

    @Override
    public Bundle readFrom(Class<Bundle> type,
                           Type genericType,
                           Annotation[] annotations,
                           MediaType mediaType,
                           MultivaluedMap<String, String> httpHeaders,
                           InputStream entityStream) throws WebApplicationException {
        return FHIR_CTX.newJsonParser().parseResource(Bundle.class, entityStream);
    }
}
