package de.servicehealth.epa4all.cxf.provider;

import de.gematik.vau.lib.VauClientStateMachine;
import de.servicehealth.epa4all.cxf.interceptor.EmptyBody;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.yasson.internal.JsonBindingBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Collectors;

import static de.servicehealth.epa4all.cxf.transport.HTTPClientVAUConduit.VAU_METHOD_PATH;

public class JSONBOctetProvider implements MessageBodyWriter {

    private final VauClientStateMachine vauClient;
    private final JsonbBuilder jsonbBuilder;

    public JSONBOctetProvider(VauClientStateMachine vauClient) {
        jsonbBuilder = new JsonBindingBuilder();
        this.vauClient = vauClient;
    }

    @Override
    public boolean isWriteable(Class type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return mediaType.equals(MediaType.APPLICATION_OCTET_STREAM_TYPE);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void writeTo(Object o, Class type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
        try (Jsonb build = jsonbBuilder.build()) {

            byte[] originPayload = new byte[0];
            if (!type.isAssignableFrom(EmptyBody.class)) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                build.toJson(o, type, os);
                originPayload = os.toByteArray();
            }

            List<String> vauPathHeaders = (List<String>) httpHeaders.remove(VAU_METHOD_PATH);
            String path = vauPathHeaders.isEmpty() ? "undefined" : vauPathHeaders.getFirst();

            String additionalHeaders = ((MultivaluedMap<String, String>) httpHeaders).entrySet()
                .stream()
                .filter(p -> !p.getKey().equals(HttpHeaders.CONTENT_TYPE))
                .filter(p -> !p.getKey().equals(HttpHeaders.ACCEPT))
                .map(p -> p.getKey() + ": " + p.getValue().getFirst())
                .collect(Collectors.joining("\r\n"));

            if (!additionalHeaders.isBlank()) {
                additionalHeaders += "\r\n";
            }

            byte[] httpRequest = (path + " HTTP/1.1\r\n"
                + "Host: localhost:443\r\n"
                + additionalHeaders
                + "Accept: application/json\r\n"
                + prepareContentHeaders(originPayload)).getBytes();

            byte[] content = ArrayUtils.addAll(httpRequest, originPayload);

            byte[] vauMessage = vauClient.encryptVauMessage(content);
            entityStream.write(vauMessage);
            entityStream.close();

        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private String prepareContentHeaders(byte[] originPayload) {
        int length = originPayload == null ? 0 :originPayload.length;
        return "Content-Type: application/json\r\nContent-Length: " + length + "\r\n\r\n";
    }
}
