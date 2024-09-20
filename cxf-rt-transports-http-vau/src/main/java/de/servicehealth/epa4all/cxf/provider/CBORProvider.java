package de.servicehealth.epa4all.cxf.provider;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;

public class CBORProvider implements MessageBodyWriter<ByteBuffer> {

    @Override
    public boolean isWriteable(Class type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return mediaType.getType().equals("application") && mediaType.getSubtype().equals("cbor");
    }

    @Override
    public void writeTo(
        ByteBuffer buffer,
        Class type,
        Type genericType,
        Annotation[] annotations,
        MediaType mediaType,
        MultivaluedMap httpHeaders,
        OutputStream entityStream
    ) throws IOException, WebApplicationException {
        entityStream.write(buffer.array());
        entityStream.close();
    }
}
