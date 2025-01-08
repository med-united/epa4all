package de.servicehealth.epa4all.cxf.provider;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;

import static de.servicehealth.vau.VauClient.VAU_ERROR;
import static de.servicehealth.vau.VauFacade.NO_USER_SESSION;

public class JsonbVauReaderProvider extends AbstractJsonbReader {

    @Override
    public boolean isReadable(Class type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return mediaType.getSubtype().contains("json");
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected byte[] getBytes(InputStream entityStream, MultivaluedMap httpHeaders) throws IOException {
        List vauErrorList = (List) httpHeaders.get(VAU_ERROR);
        if (vauErrorList != null && !vauErrorList.isEmpty()) {
            String error = (String) vauErrorList.getFirst();
            if (!error.contains(NO_USER_SESSION)) {
                throw new IOException(error);
            }
        }
        return entityStream.readAllBytes();
    }
}
