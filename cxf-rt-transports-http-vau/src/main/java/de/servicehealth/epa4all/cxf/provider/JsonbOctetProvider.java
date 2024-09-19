package de.servicehealth.epa4all.cxf.provider;

import de.gematik.vau.lib.VauClientStateMachine;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.util.encoders.Base64;
import org.eclipse.yasson.internal.JsonBindingBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.UUID;
import java.util.stream.Collectors;

public class JsonbOctetProvider implements MessageBodyReader, MessageBodyWriter {

    private final VauClientStateMachine vauClient;
    private final JsonbBuilder jsonbBuilder;
    private int msgNumber = 0;

    public JsonbOctetProvider(VauClientStateMachine vauClient) {
        jsonbBuilder = new JsonBindingBuilder();
        this.vauClient = vauClient;
    }

    @Override
    public boolean isReadable(Class type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return mediaType.equals(MediaType.APPLICATION_OCTET_STREAM_TYPE);
    }

    @Override
    public Object readFrom(Class type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
        try (Jsonb build = jsonbBuilder.build()) {
            return build.fromJson(entityStream, type);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean isWriteable(Class type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return mediaType.equals(MediaType.APPLICATION_OCTET_STREAM_TYPE);
    }

    @Override
    public void writeTo(Object o, Class type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
        try (Jsonb build = jsonbBuilder.build()) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            build.toJson(o, type, os);
            byte[] vauPayload = vauClient.encryptVauMessage(os.toByteArray());

            // TODO
            
            String path = "https://localhost:443/epa/basic/api/v1/ps/entitlements";

            MultivaluedMap<String, String> headers = httpHeaders;
            String additionalHeaders = headers.entrySet().stream().map(p -> p.getKey() + ": " + p.getValue())
                .collect(Collectors.joining("\r\n"));
            if (!additionalHeaders.isBlank()) {
                additionalHeaders += "\r\n";
            }

            byte[] httpRequest = ("POST " + path + " HTTP/1.1\r\n"
                + "Host: localhost:443\r\n"
                + additionalHeaders
                + "Content-Type: application/cbor\r\n"
                + "Content-Length: " + vauPayload.length + "\r\n\r\n").getBytes();

            byte[] tgr = makeTgr(ArrayUtils.addAll(httpRequest, vauPayload));
            entityStream.write(tgr);
            entityStream.close();

        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private byte[] makeTgr(byte[] content) {
        String rec, sen;
        if (msgNumber % 2 == 0) {
            rec = "localhost:443";
            sen = "";
        } else {
            rec = "";
            sen = "localhost:443";
        }
        String result =
            "{\"receiverHostname\":\"" + rec + "\","
                + "\"sequenceNumber\":\"" + msgNumber++ + "\","
                + "\"senderHostname\":\"" + sen + "\","
                + "\"uuid\":\"" + UUID.randomUUID() + "\","
                + "\"rawMessageContent\":\"" + Base64.toBase64String(content) + "\"}\n";
        return result.getBytes();
    }
}
