package de.servicehealth.epa4all;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class FHIRResponseVAUInterceptor implements HttpResponseInterceptor {

    private final VauClient vauClient;

    public FHIRResponseVAUInterceptor(VauClient vauClient) {
        this.vauClient = vauClient;
    }

    private boolean gzipCondition(byte[] vauBytes, int i) {
        return vauBytes[i] == 13 && vauBytes[i + 1] == 10 && vauBytes[i + 2] == 13 && vauBytes[i + 3] == 10;
    }

    @Override
    public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {
        byte[] bytes = response.getEntity().getContent().readAllBytes();
        String rawMessage = new String(bytes, UTF_8);
        if (rawMessage.contains("error") || rawMessage.contains("ERROR") || rawMessage.contains("Error")) {
            throw new HttpException(rawMessage);
        }
        byte[] vauBytes = vauClient.getVauStateMachine().decryptVauMessage(bytes);

        int i = 0;
        while (!gzipCondition(vauBytes, i) && (i + 4 < vauBytes.length)) {
            i++;
        }

        byte[] headerBytes = new byte[i];
        System.arraycopy(vauBytes, 0, headerBytes, 0, i);
        int status = Integer.parseInt(new String(headerBytes, UTF_8).split("\n")[0].split(" ")[1]);
        Header[] headers = Stream.of(new String(headerBytes, UTF_8).split("\n")).skip(1).map(s -> {
            String[] nameValue = s.split(": ");
            return (Header) new BasicHeader(nameValue[0].trim(), nameValue[1].trim());
        }).toArray(Header[]::new);

        response.setStatusCode(status);
        response.setHeaders(headers);

        if (vauBytes.length - 4 > i) {
            byte[] gzipBytes = new byte[vauBytes.length - i - 4];
            System.arraycopy(vauBytes, i + 4, gzipBytes, 0, vauBytes.length - i - 4);
            try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(gzipBytes))) {
                String fhirPayload = new String(gzipInputStream.readAllBytes());
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
    }
}
