package de.servicehealth.epa4all.medication.fhir.restful.extension.render;

import io.quarkus.logging.Log;
import org.apache.http.Header;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.message.BasicHeader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpHeaders.CONNECTION;
import static org.apache.http.HttpHeaders.UPGRADE;
import static org.apache.http.HttpHeaders.USER_AGENT;

public abstract class AbstractRenderClient implements IRenderClient {

    public static final String PDF_EXT = "pdf";
    public static final String XHTML_EXT = "xhtml";

    private final Executor executor;
    private final String medicationRenderUrl;

    public AbstractRenderClient(
        Executor executor,
        String medicationRenderUrl
    ) {
        this.executor = executor;
        this.medicationRenderUrl = medicationRenderUrl;
    }
    

    @Override
    public byte[] getPdfBytes(Map<String, String> xHeaders) throws Exception {
        try (InputStream content = execute(PDF_EXT, xHeaders)) {
            return content.readAllBytes();
        }
    }

    @Override
    public File getPdfFile(Map<String, String> xHeaders) throws Exception {
        File tempFile = File.createTempFile(UUID.randomUUID().toString(), "." + PDF_EXT, new File("."));
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            outputStream.write(getPdfBytes(xHeaders));
        }
        return tempFile;
    }

    @Override
    public byte[] getXhtmlDocument(Map<String, String> xHeaders) throws Exception {
        try (InputStream content = execute(XHTML_EXT, xHeaders)) {
            byte[] bytes = content.readAllBytes();
            Log.info(new String(bytes, StandardCharsets.UTF_8));
            return bytes;
        }
    }

    private InputStream execute(String ext, Map<String, String> xHeaders) throws Exception {
        URI renderUri = URI.create(medicationRenderUrl + "/" + ext);
        Header[] headers = prepareHeaders(xHeaders);

        Response response = executor.execute(buildRequest(renderUri, headers));
        return response.returnResponse().getEntity().getContent();
    }

    protected abstract Request buildRequest(URI renderUri, Header[] headers);

    private Header[] prepareHeaders(Map<String, String> xHeaders) {
        int total = 4 + xHeaders.size();
        Header[] headers = new Header[total];
        headers[0] = new BasicHeader(CONNECTION, "Upgrade, HTTP2-Settings");
        headers[1] = new BasicHeader(ACCEPT, "*/*");
        headers[2] = new BasicHeader(UPGRADE, "h2c");
        headers[3] = new BasicHeader(USER_AGENT, "Apache-CfxClient/4.0.5");

        Iterator<Map.Entry<String, String>> iterator = xHeaders.entrySet().iterator();
        for (int i = 4; i < total; i++) {
            Map.Entry<String, String> next = iterator.next();
            headers[i] = new BasicHeader(next.getKey(), next.getValue());
        }
        return headers;
    }
}