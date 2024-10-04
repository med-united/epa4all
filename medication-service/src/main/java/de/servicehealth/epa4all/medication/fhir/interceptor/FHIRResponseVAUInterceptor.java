package de.servicehealth.epa4all.medication.fhir.interceptor;

import de.servicehealth.epa4all.VauClient;
import de.servicehealth.epa4all.VauResponse;
import de.servicehealth.epa4all.VauResponseReader;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;

public class FHIRResponseVAUInterceptor implements HttpResponseInterceptor {

    private final VauResponseReader vauResponseReader;

    public FHIRResponseVAUInterceptor(VauClient vauClient) {
        this.vauResponseReader = new VauResponseReader(vauClient);
    }

    @Override
    public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {
        byte[] bytes = response.getEntity().getContent().readAllBytes();
        try {
            VauResponse vauResponse = vauResponseReader.read(bytes);
            Header[] headers = vauResponse.headers()
                .stream()
                .map(p -> (Header) new BasicHeader(p.getKey(), p.getValue()))
                .toArray(Header[]::new);

            response.setStatusCode(vauResponse.status());
            response.setHeaders(headers);
            byte[] payload = vauResponse.payload();
            if (payload != null) {
                Optional<Header> contentTypeOpt = Stream.of(headers)
                    .filter(h -> h.getName().equals(HttpHeaders.CONTENT_TYPE))
                    .findFirst();

                AbstractHttpEntity entity = createEntity(contentTypeOpt, payload);
                contentTypeOpt.ifPresent(header -> entity.setContentType(header.getValue()));
                response.setEntity(entity);
            }
        } catch (IllegalArgumentException e) {
            throw new HttpException(e.getMessage());
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private AbstractHttpEntity createEntity(Optional<Header> contentTypeOpt, byte[] payload) throws IOException {
        if (contentTypeOpt.isPresent()) {
            String contentType = contentTypeOpt.get().getValue();
            if (contentType.contains("pdf")) {
                return new ByteArrayEntity(payload);
            }
        }
        return new StringEntity(new String(payload, StandardCharsets.UTF_8));
    }
}