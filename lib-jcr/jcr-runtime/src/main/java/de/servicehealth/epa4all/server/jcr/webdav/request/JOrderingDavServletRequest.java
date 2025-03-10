package de.servicehealth.epa4all.server.jcr.webdav.request;

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.ordering.OrderPatch;
import org.apache.jackrabbit.webdav.ordering.OrderingConstants;
import org.apache.jackrabbit.webdav.ordering.Position;

public interface JOrderingDavServletRequest extends JDavServletRequest {

    /**
     * Returns the {@link OrderingConstants#HEADER_ORDERING_TYPE Ordering-Type header}.
     *
     * @return the String value of the {@link OrderingConstants#HEADER_ORDERING_TYPE Ordering-Type header}.
     */
    String getOrderingType();

    /**
     * Return a <code>Position</code> object encapsulating the {@link OrderingConstants#HEADER_POSITION
     * Position header} field or <code>null</code> if no Position header is present
     * or does not contain a valid format.
     *
     * @return <code>Position</code> object encapsulating the {@link OrderingConstants#HEADER_POSITION
     * Position header}
     */
    Position getPosition();

    /**
     * Return a <code>OrderPatch</code> object encapsulating the request body
     * of an ORDERPATCH request or <code>null</code> if the request body was
     * either missing or could not be parsed.
     *
     * @return <code>OrderPatch</code> object encapsulating the request body.
     */
    OrderPatch getOrderPatch() throws DavException;
}