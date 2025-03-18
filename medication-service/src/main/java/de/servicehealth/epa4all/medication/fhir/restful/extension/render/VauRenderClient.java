package de.servicehealth.epa4all.medication.fhir.restful.extension.render;

import de.servicehealth.folder.IFolderService;
import org.apache.http.Header;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;

import java.net.URI;

public class VauRenderClient extends AbstractRenderClient {

    public VauRenderClient(
        Executor executor,
        String epaUserAgent,
        String medicationRenderUrl,
        IFolderService folderService
    ) {
        super(executor, epaUserAgent, medicationRenderUrl, folderService);
    }

    @Override
    protected Request buildRequest(URI renderUri, Header[] headers) {
        /*
         * If we construct Request directly as Request.Get/Request.Post/Etc then it bypasses
         * ApacheHttpClient.createHttpRequest procedure, so for direct construction it
         * should be beared in mind that for VAU we always need Request.Post
         */
        return Request.Post(renderUri).setHeaders(headers);
    }
}