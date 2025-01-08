package de.servicehealth.epa4all.medication.fhir.interceptor;

import de.servicehealth.vau.VauFacade;
import de.servicehealth.vau.VauResponse;
import de.servicehealth.vau.VauResponseReader;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static de.servicehealth.vau.VauFacade.NO_USER_SESSION;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;

public class FHIRResponseVAUInterceptor implements HttpResponseInterceptor {

    private static final Logger log = Logger.getLogger(FHIRResponseVAUInterceptor.class.getName());

    private final VauFacade vauFacade;
    private final VauResponseReader vauResponseReader;

    public FHIRResponseVAUInterceptor(VauFacade vauFacade) {
        this.vauFacade = vauFacade;
        this.vauResponseReader = new VauResponseReader(vauFacade);
    }

    @Override
    public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {
        byte[] bytes = response.getEntity().getContent().readAllBytes();
        List<Pair<String, String>> originHeaders = Arrays.stream(response.getAllHeaders())
            .map(h -> Pair.of(h.getName(), h.getValue()))
            .toList();
        int responseCode = response.getStatusLine().getStatusCode();

        String uri = ((HttpClientContext) context).getRequest().getRequestLine().getUri();
        String vauCid = URI.create(uri).getPath();

        VauResponse vauResponse = vauResponseReader.read(vauCid, responseCode, originHeaders, bytes);
        String error = vauResponse.error();
        if (error != null) {
            String msg = "Error while receiving DIRECT Fhir response, decrypted = " + vauResponse.decrypted() + " : " + error;
            log.log(Level.SEVERE, msg);
            boolean noUserSession = error.contains(NO_USER_SESSION);
            vauFacade.steadyVauSession(vauCid, noUserSession, vauResponse.decrypted());
        }

        Header[] headers = vauResponse.headers()
            .stream()
            .map(p -> (Header) new BasicHeader(p.getKey(), p.getValue()))
            .toArray(Header[]::new);

        response.setStatusCode(vauResponse.status());
        response.setHeaders(headers);
        byte[] payload = vauResponse.payload();
        if (payload != null) {
            Optional<Header> contentTypeOpt = Stream.of(headers)
                .filter(h -> h.getName().equalsIgnoreCase(CONTENT_TYPE))
                .findFirst();

            AbstractHttpEntity entity = new ByteArrayEntity(payload);
            contentTypeOpt.ifPresent(header -> entity.setContentType(header.getValue()));
            response.setEntity(entity);
        }
    }
}