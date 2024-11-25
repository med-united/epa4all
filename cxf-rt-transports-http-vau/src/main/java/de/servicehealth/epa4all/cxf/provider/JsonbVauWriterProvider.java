package de.servicehealth.epa4all.cxf.provider;

import de.servicehealth.epa4all.cxf.interceptor.EmptyBody;
import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauFacade;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.ws.rs.WebApplicationException;
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
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static de.servicehealth.epa4all.cxf.transport.HTTPClientVauConduit.VAU_METHOD_PATH;
import static de.servicehealth.vau.VauClient.VAU_CID;
import static de.servicehealth.vau.VauClient.VAU_NON_PU_TRACING;
import static de.servicehealth.vau.VauClient.VAU_NP;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM_TYPE;

@SuppressWarnings("rawtypes")
public class JsonbVauWriterProvider implements MessageBodyWriter {

    private final VauFacade vauFacade;
    private final JsonbBuilder jsonbBuilder;

    private static final Logger log = Logger.getLogger(JsonbVauWriterProvider.class.getName());

    public JsonbVauWriterProvider(VauFacade vauFacade) {
        jsonbBuilder = new JsonBindingBuilder();
        this.vauFacade = vauFacade;
    }

    @Override
    public boolean isWriteable(Class type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return mediaType.equals(APPLICATION_OCTET_STREAM_TYPE);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void writeTo(
        Object o,
        Class type,
        Type genericType,
        Annotation[] annotations,
        MediaType mediaType,
        MultivaluedMap httpHeaders,
        OutputStream entityStream
    ) throws IOException, WebApplicationException {
        try (Jsonb build = jsonbBuilder.build()) {

            byte[] originPayload = new byte[0];
            if (!type.isAssignableFrom(EmptyBody.class)) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                build.toJson(o, type, os);
                originPayload = os.toByteArray();
            }

            String path = evictHeader(httpHeaders, VAU_METHOD_PATH);
            String vauCid = evictHeader(httpHeaders, VAU_CID);
            String backend = evictHeader(httpHeaders, X_BACKEND);

            if (!vauFacade.isTracingEnabled()) {
                httpHeaders.remove(VAU_NON_PU_TRACING);
            }

            String additionalHeaders = ((MultivaluedMap<String, String>) httpHeaders).entrySet()
                .stream()
                .filter(p -> !p.getKey().equals(CONTENT_TYPE))
                .filter(p -> !p.getKey().equals(ACCEPT))
                .map(p -> p.getKey() + ": " + p.getValue().getFirst())
                .collect(Collectors.joining("\r\n"));

            httpHeaders.remove(X_INSURANT_ID);
            httpHeaders.remove(VAU_NP);

            if (!additionalHeaders.isBlank()) {
                additionalHeaders += "\r\n";
            }

            String keepAlive = additionalHeaders.contains("Keep-Alive") ? "" : "Connection: Keep-Alive\r\n";

            byte[] httpRequest = (path + " HTTP/1.1\r\n"
                + "Host: " + backend + "\r\n"
                + additionalHeaders + keepAlive
                + "Accept: application/json\r\n"
                + prepareContentHeaders(originPayload)).getBytes();

            byte[] content = ArrayUtils.addAll(httpRequest, originPayload);

            log.info("REST Inner Request: " + new String(content));

            VauClient vauClient = vauFacade.getVauClient(vauCid);
            byte[] vauMessage = vauClient.getVauStateMachine().encryptVauMessage(content);
            entityStream.write(vauMessage);
            entityStream.close();

        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private String evictHeader(MultivaluedMap httpHeaders, String headerName) {
        List<String> targetHeaders = (List<String>) httpHeaders.remove(headerName);
        return targetHeaders == null || targetHeaders.isEmpty() ? null : targetHeaders.getFirst();
    }

    private String prepareContentHeaders(byte[] originPayload) {
        int length = originPayload == null ? 0 : originPayload.length;
        return "Content-Type: application/json\r\nContent-Length: " + length + "\r\n\r\n";
    }
}
