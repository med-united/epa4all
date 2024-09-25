package de.servicehealth.epa4all;

import de.gematik.vau.lib.VauClientStateMachine;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.entity.DecompressingEntity;
import org.apache.http.client.entity.GZIPInputStreamFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class FHIRResponseVAUInterceptor implements HttpResponseInterceptor {

    private final VauClientStateMachine vauClient;

    public FHIRResponseVAUInterceptor(VauClientStateMachine vauClient) {
        this.vauClient = vauClient;
    }

    private boolean gzipCondition(byte[] vauBytes, int i) {
        return vauBytes[i] == 13 && vauBytes[i + 1] == 10 && vauBytes[i + 2] == 13 && vauBytes[i + 3] == 10;
    }

    @Override
    public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {
        byte[] bytes = response.getEntity().getContent().readAllBytes();
        byte[] vauBytes = vauClient.decryptVauMessage(bytes);

        int i = 0;
        while (!gzipCondition(vauBytes, i) && (i + 4 < vauBytes.length)) {
            i++;
        }

        if (vauBytes.length - 4 <= i) {
            System.out.println("no payload");
        } else {
            byte[] headerBytes = new byte[i];
            System.arraycopy(vauBytes, 0, headerBytes, 0, i);
            int status = Integer.parseInt(new String(headerBytes, UTF_8).split("\n")[0].split(" ")[1]);
            Header[] headers = Stream.of(new String(headerBytes, UTF_8).split("\n")).skip(1).map(s -> {
                String[] nameValue = s.split(": ");
                return (Header) new BasicHeader(nameValue[0].trim(), nameValue[1].trim());
            }).toArray(Header[]::new);
            byte[] gzipBytes = new byte[vauBytes.length - i - 4];
            System.arraycopy(vauBytes, i + 4, gzipBytes, 0, vauBytes.length - i - 4);
            try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(gzipBytes))) {
                String fhirPayload = new String(gzipInputStream.readAllBytes());

                // response.setEntity(new DecompressingEntity(response.getEntity(), GZIPInputStreamFactory.getInstance()));

                response.setStatusCode(status);
                response.setHeaders(headers);
                StringEntity entity = new StringEntity(fhirPayload);
                String contentType = Arrays.stream(headers)
                    .filter(h -> h.getName().equals(HttpHeaders.CONTENT_TYPE))
                    .findFirst()
                    .get()
                    .getValue();
                entity.setContentType(contentType);
                response.setEntity(entity);
            }
        }

        //  &&

        /*
            HTTP/1.1 201 Created
            X-Powered-By: HAPI FHIR 7.2.0 REST Server (FHIR Server; FHIR 4.0.1/R4)
            ETag: W/"1"
            X-Request-ID: i0ytG1XBinkIwEgS
            Content-Location: http://medication-service:8080/fhir/Patient/14/_history/1
            Last-Modified: Tue, 24 Sep 2024 14:32:23 GMT
            Location: http://medication-service:8080/fhir/Patient/14/_history/1
            Content-Encoding: gzip  
            Content-Type: application/fhir+json;charset=UTF-8
            Date: Tue, 24 Sep 2024 14:32:23 GMT
            Keep-Alive: timeout=60
            Connection: keep-alive
            content-length: 298
         */



    }
}
