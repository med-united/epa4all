package de.servicehealth.epa4all.cxf.provider;

import de.servicehealth.epa4all.cxf.model.FhirResponse;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.yasson.internal.JsonBindingBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.LOWER_HYPHEN;
import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static de.servicehealth.utils.ServerUtils.extractHeaders;
import static de.servicehealth.vau.VauClient.VAU_STATUS;
import static java.nio.charset.StandardCharsets.UTF_8;

@SuppressWarnings("rawtypes")
public abstract class AbstractJsonbReader implements MessageBodyReader {

    private final JsonbBuilder jsonbBuilder;
    private final JsonbConfig config;

    public AbstractJsonbReader() {
        jsonbBuilder = new JsonBindingBuilder();
        config = new JsonbConfig()
            .withPropertyNamingStrategy(propertyName -> switch (propertyName) {
                    case "vauNp" -> LOWER_CAMEL.to(LOWER_HYPHEN, propertyName);
                    case "accessToken", "tokenType", "expiresIn" -> LOWER_CAMEL.to(LOWER_UNDERSCORE, propertyName);
                    default -> propertyName;
                }
            );
    }

    protected byte[] getBytes(InputStream entityStream, MultivaluedMap httpHeaders) throws VauException, IOException {
        return entityStream.readAllBytes();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object readFrom(
        Class type, Type genericType, Annotation[] annotations, MediaType mediaType,
        MultivaluedMap httpHeaders, InputStream entityStream
    ) throws IOException, WebApplicationException {
        try (Jsonb build = jsonbBuilder.withConfig(config).build()) {
            byte[] bytes = getBytes(entityStream, httpHeaders);
            if (type.isAssignableFrom(FhirResponse.class)) {
                List<Pair<String, String>> headers = extractHeaders(httpHeaders, Set.of());
                int status = parseHttpStatus(httpHeaders);
                return new FhirResponse(headers, bytes, status);
            } else {
                String payload = new String(bytes, UTF_8);
                if (payload.startsWith("[")) {
                    payload = String.format("{\"data\": %s }", payload);
                }
                return build.fromJson(payload, type);
            }
        } catch (IOException e) {
            throw e;
        } catch (VauException e) {
            throw new IOException(e);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private int parseHttpStatus(MultivaluedMap httpHeaders) {
        try {
            String vauStatus = (String) httpHeaders.getFirst(VAU_STATUS);
            return Integer.parseInt(vauStatus);
        } catch (Exception e) {
            return 200;
        }
    }
}
