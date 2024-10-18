package de.servicehealth.epa4all.cxf.provider;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;

import static de.servicehealth.epa4all.cxf.interceptor.CxfVauReadInterceptor.VAU_ERROR;
import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM_TYPE;

public class JsonbVauReaderProvider extends AbstractJsonbReader {

    @Override
    public boolean isReadable(Class type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return mediaType.equals(APPLICATION_OCTET_STREAM_TYPE);
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected byte[] getBytes(InputStream entityStream, MultivaluedMap httpHeaders) throws IOException {
        List vauErrorList = (List) httpHeaders.get(VAU_ERROR);
        if (vauErrorList != null && !vauErrorList.isEmpty()) {
            throw new IOException((String) vauErrorList.getFirst());
        }
        return entityStream.readAllBytes();
    }
}
