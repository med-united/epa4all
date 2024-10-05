package de.servicehealth.epa4all.cxf.provider;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import org.eclipse.yasson.internal.JsonBindingBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

public class JsonbVauReaderProvider implements MessageBodyReader {

    private final JsonbBuilder jsonbBuilder;

    public JsonbVauReaderProvider() {
        jsonbBuilder = new JsonBindingBuilder();
    }

    @Override
    public boolean isReadable(Class type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return mediaType.equals(MediaType.APPLICATION_OCTET_STREAM_TYPE);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object readFrom(
        Class type, Type genericType, Annotation[] annotations, MediaType mediaType,
        MultivaluedMap httpHeaders, InputStream entityStream
    ) throws IOException, WebApplicationException {
        byte[] bytes = entityStream.readAllBytes();
        try (Jsonb build = jsonbBuilder.build()) {
            String payload = new String(bytes, StandardCharsets.UTF_8);
            if (payload.startsWith("[")) {
                payload = String.format("{\"data\": %s }", payload);
            }
            return build.fromJson(payload, type);
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }
}
