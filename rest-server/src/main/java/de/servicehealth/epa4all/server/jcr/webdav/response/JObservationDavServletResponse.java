package de.servicehealth.epa4all.server.jcr.webdav.response;

import org.apache.jackrabbit.webdav.observation.EventDiscovery;
import org.apache.jackrabbit.webdav.observation.Subscription;

import java.io.IOException;

@SuppressWarnings("JavadocDeclaration")
public interface JObservationDavServletResponse extends JDavServletResponse {

    /**
     * Send the response to a successful SUBSCRIBE request.
     *
     * @param subscription that needs to be represented in the response body.
     * @throws IOException
     */
    public void sendSubscriptionResponse(Subscription subscription) throws IOException;

    /**
     * Send the response to a successful POLL request.
     *
     * @param eventDiscovery {@link EventDiscovery} object to be returned in
     * the response body.
     * @throws IOException
     */
    public void sendPollResponse(EventDiscovery eventDiscovery) throws IOException;
}
