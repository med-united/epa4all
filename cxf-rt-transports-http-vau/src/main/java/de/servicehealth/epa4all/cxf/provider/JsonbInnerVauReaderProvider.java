package de.servicehealth.epa4all.cxf.provider;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

import static de.servicehealth.vau.VauClient.VAU_ERROR;

public class JsonbInnerVauReaderProvider extends AbstractJsonbReader {

    private static final Set<String> MEDIA_TYPES = Set.of("pdf", "json", "text", "html", "xhtml");

    @Override
    public boolean isReadable(Class type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return MEDIA_TYPES.stream().anyMatch(mediaType.getSubtype()::contains);
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected byte[] getBytes(InputStream entityStream, MultivaluedMap httpHeaders) throws VauException, IOException {
        List vauErrorList = (List) httpHeaders.get(VAU_ERROR);
        if (vauErrorList == null || vauErrorList.isEmpty()) {
            return entityStream.readAllBytes();
        }
        String error = (String) vauErrorList.getFirst();
        throw new VauException(error);
    }
}
