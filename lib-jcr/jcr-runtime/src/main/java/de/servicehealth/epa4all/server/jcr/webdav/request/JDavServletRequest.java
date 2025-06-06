package de.servicehealth.epa4all.server.jcr.webdav.request;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.apache.jackrabbit.webdav.DavSession;
import org.apache.jackrabbit.webdav.lock.LockInfo;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.PropEntry;
import org.w3c.dom.Document;

import java.util.List;

@SuppressWarnings({"JavadocDeclaration", "unused"})
public interface JDavServletRequest extends HttpServletRequest {

    /**
     * Sets the <code>DavSession</code> to this request.
     *
     * @param session
     */
    void setDavSession(DavSession session);

    /**
     * Returns the {@link DavSession} created for this request.
     *
     * @return session for this resource
     */
    DavSession getDavSession();

    /**
     * Return the locator of the requested {@link DavResource resource}.
     *
     * @return locator of the requested {@link DavResource resource}.
     */
    DavResourceLocator getRequestLocator();

    /**
     * Parse the {@link DavConstants#HEADER_DESTINATION Destination header}
     * and return the locator of the corresponding {@link DavResource resource}.
     *
     * @return locator of the resource specified with the Destination header.
     * @see DavConstants#HEADER_DESTINATION
     */
    DavResourceLocator getDestinationLocator() throws DavException;

    /**
     * Returns true if the {@link DavConstants#HEADER_OVERWRITE Overwrite header}
     * is set to 'T' thus instructing the server to overwrite the state of a
     * non-null destination resource during a COPY or MOVE. A Overwrite header
     * value of 'F' will return false.
     *
     * @return true if the Overwrite header is set to 'T', false if it is set
     * to 'F'.
     * @see DavConstants#HEADER_OVERWRITE
     */
    boolean isOverwrite();

    /**
     * Return the integer representation of the given {@link DavConstants#HEADER_DEPTH
     * Depth header}. 'Infinity' is represented by {@link DavConstants#DEPTH_INFINITY}.
     *
     * @return integer representation of the {@link DavConstants#HEADER_DEPTH
     * Depth header}.
     * @see DavConstants#HEADER_DEPTH
     */
    int getDepth();

    /**
     * Returns the integer representation of the {@link DavConstants#HEADER_DEPTH
     * Depth header} or the given defaultValue, if the Depth header is missing.
     *
     * @param defaultValue to be returned if no Depth header is present.
     * @return integer representation of the {@link DavConstants#HEADER_DEPTH
     * Depth header} or the given defaultValue.
     * @see DavConstants#HEADER_DEPTH
     */
    int getDepth(int defaultValue);

    /**
     * Returns the token present in the {@link DavConstants#HEADER_LOCK_TOKEN
     * Lock-Token Header} or <code>null</code> if no such header is available.<br>
     * Note: The 'Lock-Token' header is sent with UNLOCK requests and with
     * lock responses only. For any other request that may be affected by a lock
     * the 'If' header field is responsible.
     *
     * @return the token present in the Lock-Token header.
     * @see DavConstants#HEADER_LOCK_TOKEN
     */
    String getLockToken();

    /**
     * Return the timeout requested in the {@link DavConstants#HEADER_TIMEOUT
     * Timeout header} as <code>long</code>. The representation of the
     * 'Infinite' timeout is left to the implementation.
     *
     * @return long value representation of the Timeout header.
     * @see DavConstants#HEADER_TIMEOUT
     * @see DavConstants#TIMEOUT_INFINITE
     */
    long getTimeout();

    /**
     * Parse the Xml request body and return a {@link org.w3c.dom.Document}.
     *
     * @return Document representing the Xml request body or <code>null</code>
     * if no request body is present.
     * @throws DavException If the request body cannot be parsed into an Xml
     * Document.
     */
    Document getRequestDocument() throws DavException;

    /**
     * Return the type of PROPFIND request as indicated by the PROPFIND request
     * body.
     *
     * @return type of PROPFIND request
     * @see DavConstants#PROPFIND_ALL_PROP
     * @see DavConstants#PROPFIND_BY_PROPERTY
     * @see DavConstants#PROPFIND_PROPERTY_NAMES
     * @see DavConstants#PROPFIND_ALL_PROP_INCLUDE
     * @throws DavException If the propfind type could not be determined due to
     * an invalid request body.
     */
    int getPropFindType() throws DavException;

    /**
     * Return the set of properties the client requested with a PROPFIND request
     * or an empty set if the type of PROPFIND request was {@link DavConstants#PROPFIND_ALL_PROP}
     * or {@link DavConstants#PROPFIND_PROPERTY_NAMES}.
     *
     * @return set of properties the client requested with a PROPFIND request
     * @throws DavException In case of invalid request body
     */
    DavPropertyNameSet getPropFindProperties() throws DavException;

    /**
     * Return a {@link List} of property change operations. Each entry
     * is either of type {@link DavPropertyName}, indicating a &lt;remove&gt;
     * operation, or of type {@link DavProperty}, indicating a &lt;set&gt;
     * operation. Note that ordering is significant here.
     *
     * @return {@link List} of property change operations
     * @throws DavException In case of invalid request body
     */
    List<? extends PropEntry> getPropPatchChangeList() throws DavException;

    /**
     * Return the parsed 'lockinfo' request body, the {@link DavConstants#HEADER_TIMEOUT
     * Timeout header} and the {@link DavConstants#HEADER_DEPTH Depth header}
     * of a LOCK request as <code>LockInfo</code> object.
     *
     * @return <code>LockInfo</code> object encapsulating the information
     * present in the LOCK request.
     * @see DavConstants#HEADER_TIMEOUT
     * @see DavConstants#HEADER_DEPTH
     * @see DavConstants#XML_LOCKINFO
     * @throws DavException
     */
    LockInfo getLockInfo() throws DavException;

    /**
     * Returns true, if the {@link DavConstants#HEADER_IF If header} present
     * with the request matches the given resource.
     *
     * @param resource
     * @return true, if the test is successful, false otherwise.
     */
    boolean matchesIfHeader(DavResource resource);

    /**
     * Returns true, if the {@link DavConstants#HEADER_IF If header} present
     * with the request matches to the given href, token and eTag.
     *
     * @param href
     * @param token
     * @param eTag
     * @return true, if the test is successful, false otherwise.
     */
    boolean matchesIfHeader(String href, String token, String eTag);
}