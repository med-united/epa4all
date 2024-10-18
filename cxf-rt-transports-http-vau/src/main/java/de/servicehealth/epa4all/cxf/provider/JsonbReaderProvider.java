package de.servicehealth.epa4all.cxf.provider;

import jakarta.ws.rs.core.MediaType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class JsonbReaderProvider extends AbstractJsonbReader {

    @Override
    public boolean isReadable(Class type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return mediaType.toString().contains(APPLICATION_JSON);
    }
}
