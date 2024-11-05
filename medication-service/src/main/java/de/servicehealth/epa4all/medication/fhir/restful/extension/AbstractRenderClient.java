package de.servicehealth.epa4all.medication.fhir.restful.extension;

import org.apache.http.Header;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.message.BasicHeader;

import io.quarkus.logging.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpHeaders.CONNECTION;
import static org.apache.http.HttpHeaders.UPGRADE;
import static org.apache.http.HttpHeaders.USER_AGENT;

public abstract class AbstractRenderClient implements IRenderClient {

    public static final String X_INSURANT_ID = "x-insurantid";
    public static final String X_USER_AGENT = "x-useragent";

    public static final String PDF_EXT = "pdf";
    public static final String XHTML_EXT = "xhtml";

    private final Executor executor;
    private final String medicationServiceRenderUrl;
	private String np;

    public AbstractRenderClient(Executor executor, String medicationServiceRenderUrl) {
        this.executor = executor;
        this.medicationServiceRenderUrl = medicationServiceRenderUrl;
    }
    

    @Override
    public byte[] getPdfBytes(String xInsurantid, String xUseragent, String np) throws Exception {
		this.np = np;
		return getPdfBytes(xInsurantid, xUseragent);
	}

    @Override
    public byte[] getPdfBytes(String xInsurantid, String xUseragent) throws Exception {
        try (InputStream content = execute(PDF_EXT, xInsurantid, xUseragent)) {
            return content.readAllBytes();
        }
    }

    @Override
    public File getPdfFile(String xInsurantid, String xUseragent) throws Exception {
        File tempFile = File.createTempFile(UUID.randomUUID().toString(), "." + PDF_EXT, new File("."));
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            outputStream.write(getPdfBytes(xInsurantid, xUseragent));
        }
        return tempFile;
    }

    @Override
    public byte[] getXhtmlDocument(String xInsurantid, String xUseragent, String np) throws Exception {
    	this.np = np;
    	return getXhtmlDocument(xInsurantid, xUseragent);
    	
    }
    
    public byte[] getXhtmlDocument(String xInsurantid, String xUseragent) throws Exception {
        try (InputStream content = execute(XHTML_EXT, xInsurantid, xUseragent)) {
            byte[] bytes = content.readAllBytes();
            Log.info(new String(bytes, StandardCharsets.UTF_8));
            return bytes;
        }
    }

    private InputStream execute(String ext, String xInsurantid, String xUseragent) throws Exception {
        URI renderUri = URI.create(medicationServiceRenderUrl + "/" + ext);
        Header[] headers = prepareHeaders(xInsurantid, xUseragent);

        Response response = executor.execute(buildRequest(renderUri, headers));
        return response.returnResponse().getEntity().getContent();
    }

    protected abstract Request buildRequest(URI renderUri, Header[] headers);

    private Header[] prepareHeaders(String xInsurantid, String xUseragent) {
        Header[] headers = new Header[7];
        headers[0] = new BasicHeader(CONNECTION, "Upgrade, HTTP2-Settings");
        headers[1] = new BasicHeader(ACCEPT, "*/*");
        headers[2] = new BasicHeader(UPGRADE, "h2c");
        headers[3] = new BasicHeader(USER_AGENT, "Apache-CfxClient/4.0.5");
        headers[4] = new BasicHeader(X_INSURANT_ID, xInsurantid);
        headers[5] = new BasicHeader(X_USER_AGENT, xUseragent);
        headers[6] = new BasicHeader("VAU-NP", np);
        return headers;
    }
}
