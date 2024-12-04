package de.servicehealth.epa4all.cxf.provider;

import de.servicehealth.epa4all.cxf.model.EmptyRequest;
import de.servicehealth.epa4all.cxf.model.FhirRequest;
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
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
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
        Object obj,
        Class type,
        Type genericType,
        Annotation[] annotations,
        MediaType mediaType,
        MultivaluedMap httpHeaders,
        OutputStream entityStream
    ) throws IOException, WebApplicationException {

        boolean isFhir = type.isAssignableFrom(FhirRequest.class);
        FhirRequest fhirRequest = isFhir ? (FhirRequest) obj : null;

        try (Jsonb build = jsonbBuilder.build()) {
            byte[] originPayload = new byte[0];
            if (!type.isAssignableFrom(EmptyRequest.class)) {
                if (isFhir) {
                    originPayload = fhirRequest.getBody();
                } else {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    build.toJson(obj, type, os);
                    originPayload = os.toByteArray();
                }
            }

            String path = evictHeader(httpHeaders, VAU_METHOD_PATH);
            if (path != null && fhirRequest != null) {
                String fhirMethod = fhirRequest.isGet() ? "GET" : "POST";
                String pathMethod = path.trim().split(" ")[0];
                path = path.replace(pathMethod, fhirMethod);
            }

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
                + additionalHeaders
                + keepAlive
                + prepareAcceptHeader(fhirRequest)
                + prepareContentHeaders(fhirRequest, originPayload)).getBytes();

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

    private String prepareAcceptHeader(FhirRequest fhirRequest) {
        if (fhirRequest == null) {
            return "Accept: application/json\r\n";
        } else {
            return fhirRequest.getAccept() + "\r\n";
        }
    }

    private String prepareContentHeaders(FhirRequest fhirRequest, byte[] originPayload) {
        int length = originPayload == null ? 0 : originPayload.length;
        String contentType = fhirRequest == null ? APPLICATION_JSON : fhirRequest.getContentType();
        if (length == 0 || contentType.isEmpty()) {
            return "\r\n";
        } else {
            return String.format("Content-Type: %s\r\nContent-Length: %d\r\n\r\n", contentType, length);
        }
    }
}
