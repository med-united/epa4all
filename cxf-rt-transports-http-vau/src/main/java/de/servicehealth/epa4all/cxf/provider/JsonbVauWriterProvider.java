package de.servicehealth.epa4all.cxf.provider;

import de.servicehealth.epa4all.cxf.VauHeaders;
import de.servicehealth.epa4all.cxf.model.EpaRequest;
import de.servicehealth.epa4all.cxf.model.FhirRequest;
import de.servicehealth.http.HttpParcel;
import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauFacade;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.yasson.internal.JsonBindingBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.servicehealth.epa4all.cxf.transport.HTTPClientVauConduit.VAU_METHOD_PATH;
import static de.servicehealth.utils.ServerUtils.isAuthError;
import static de.servicehealth.vau.VauClient.VAU_CID;
import static de.servicehealth.vau.VauClient.VAU_NP;
import static de.servicehealth.vau.VauClient.X_BACKEND;
import static de.servicehealth.vau.VauClient.X_INSURANT_ID;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_LENGTH;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM_TYPE;

@SuppressWarnings({"rawtypes", "unchecked"})
public class JsonbVauWriterProvider implements MessageBodyWriter, VauHeaders {

    private static final Logger log = LoggerFactory.getLogger(JsonbVauWriterProvider.class.getName());

    private final VauFacade vauFacade;
    private final JsonbBuilder jsonbBuilder;
    private final Set<String> maskedHeaders;
    private final Set<String> maskedAttributes;

    public JsonbVauWriterProvider(VauFacade vauFacade, Set<String> maskedHeaders, Set<String> maskedAttributes) {
        jsonbBuilder = new JsonBindingBuilder();
        this.maskedHeaders = new HashSet<>(maskedHeaders);
        this.maskedAttributes = new HashSet<>(maskedAttributes);
        this.vauFacade = vauFacade;
    }

    @Override
    public boolean isWriteable(Class type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return mediaType.equals(APPLICATION_OCTET_STREAM_TYPE);
    }

    private byte[] getPayload(Object obj, Class<?> type) throws Exception {
        if (obj instanceof EpaRequest epaRequest) {
            return epaRequest.getBody();
        } else {
            try (Jsonb jsonb = jsonbBuilder.build()) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                jsonb.toJson(obj, type, os);
                return os.toByteArray();
            }
        }
    }

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
        boolean encrypted = false;
        String vauCid = evictHeader(httpHeaders, VAU_CID);
        try {
            String methodWithPath = evictHeader(httpHeaders, VAU_METHOD_PATH);
            String backend = evictHeader(httpHeaders, X_BACKEND);

            byte[] payload = getPayload(obj, type);

            List<Pair<String, String>> innerHeaders = prepareInnerHeaders(httpHeaders, backend);
            innerHeaders.addAll(prepareAcceptHeaders(obj));
            innerHeaders.addAll(prepareContentHeaders(obj, payload));

            httpHeaders.remove(X_INSURANT_ID);
            httpHeaders.remove(VAU_NP);
            httpHeaders.remove("Connection");
            httpHeaders.remove("Upgrade");

            String statusLine = getStatusLine(obj, methodWithPath);
            HttpParcel httpParcel = new HttpParcel(statusLine, innerHeaders, payload);

            log.info("REST Inner Request: " + httpParcel.toString(false, true, maskedHeaders, maskedAttributes));

            byte[] vauMessage;
            try {
                VauClient vauClient = vauFacade.getVauClient(vauCid);
                vauMessage = vauClient.encryptVauMessage(httpParcel.toBytes());
            } finally {
                encrypted = true;
            }
            entityStream.write(vauMessage);
            entityStream.close();

        } catch (Exception e) {
            log.error("Error while sending Vau REST message", e);
            if (encrypted) {
                boolean noUserSession = isAuthError(e.getMessage());
                vauFacade.handleVauSession(vauCid, noUserSession, false);
            }
            throw new IOException(e);
        }
    }

    private String getStatusLine(Object obj, String methodWithPath) {
        if (methodWithPath != null && obj instanceof FhirRequest fhirRequest) {
            String method = methodWithPath.trim().split(" ")[0];
            methodWithPath = methodWithPath.replace(method, fhirRequest.getMethod());
        }
        return methodWithPath + " HTTP/1.1";
    }

    private List<Pair<String, String>> prepareAcceptHeaders(Object obj) {
        if (obj instanceof FhirRequest fhirRequest) {
            return fhirRequest.getAcceptHeaders();
        } else {
            return List.of(Pair.of(ACCEPT, "application/json"));
        }
    }

    private List<Pair<String, String>> prepareContentHeaders(Object obj, byte[] payload) {
        if (obj instanceof FhirRequest fhirRequest) {
            return fhirRequest.getContentHeaders();
        }
        if (payload == null || payload.length == 0) {
            return List.of();
        } else {
            return List.of(
                Pair.of(CONTENT_TYPE, APPLICATION_JSON),
                Pair.of(CONTENT_LENGTH, String.valueOf(payload.length))
            );
        }
    }
}
