package de.servicehealth.epa4all.server.jcr.webdav.request;

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.observation.ObservationConstants;
import org.apache.jackrabbit.webdav.observation.SubscriptionInfo;

public interface JObservationDavServletRequest extends JDavServletRequest {

    /**
     * Return the {@link ObservationConstants#HEADER_SUBSCRIPTIONID SubscriptionId header}
     * or <code>null</code> if no such header is present.
     *
     * @return the {@link ObservationConstants#HEADER_SUBSCRIPTIONID SubscriptionId header}
     */
    String getSubscriptionId();

    /**
     * Returns the {@link ObservationConstants#HEADER_POLL_TIMEOUT PollTimeout header}
     * or 0 (zero) if no such header is present.
     *
     * @return milliseconds indicating length of the poll timeout.
     */
    long getPollTimeout();

    /**
     * Return a {@link SubscriptionInfo} object representing the subscription
     * info present in the SUBSCRIBE request body or <code>null</code> if
     * retrieving the subscription info fails.
     *
     * @return subscription info object encapsulating the SUBSCRIBE request body
     * or <code>null</code> if the subscription info cannot be built.
     * @throws DavException if an invalid request body was encountered.
     */
    SubscriptionInfo getSubscriptionInfo() throws DavException;
}