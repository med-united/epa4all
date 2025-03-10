package de.servicehealth.epa4all.server.jcr.webdav.response;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.header.CodedUrlHeader;
import org.apache.jackrabbit.webdav.header.Header;
import org.apache.jackrabbit.webdav.lock.ActiveLock;
import org.apache.jackrabbit.webdav.lock.LockDiscovery;
import org.apache.jackrabbit.webdav.observation.EventDiscovery;
import org.apache.jackrabbit.webdav.observation.ObservationConstants;
import org.apache.jackrabbit.webdav.observation.Subscription;
import org.apache.jackrabbit.webdav.observation.SubscriptionDiscovery;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.XmlSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.transform.TransformerException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;

import static de.servicehealth.utils.XmlUtils.createDocument;

/**
 * WebdavResponseImpl implements the <code>WebdavResponse</code> interface.
 */
@SuppressWarnings("JavadocDeclaration")
public class JWebdavResponseImpl implements JWebdavResponse {

    private static final Logger log = LoggerFactory.getLogger(JWebdavResponseImpl.class);

    private final HttpServletResponse httpResponse;

    /**
     * Create a new <code>WebdavResponse</code>
     *
     * @param httpResponse
     */
    public JWebdavResponseImpl(HttpServletResponse httpResponse) {
        this(httpResponse, false);
    }

    /**
     * Create a new <code>WebdavResponse</code>
     *
     * @param httpResponse
     * @param noCache
     */
    public JWebdavResponseImpl(HttpServletResponse httpResponse, boolean noCache) {
        this.httpResponse = httpResponse;
        if (noCache) {
            /* set cache control headers */
            addHeader("Pragma", "No-cache");  // http1.0
            addHeader("Cache-Control", "no-cache"); // http1.1
        }
    }

    /**
     * If the specifid exception provides an error condition an Xml response body
     * is sent providing more detailed information about the error. Otherwise only
     * the error code and status phrase is sent back.
     */
    public void sendError(DavException exception) throws IOException {
        if (!exception.hasErrorCondition()) {
            httpResponse.sendError(exception.getErrorCode(), exception.getStatusPhrase());
        } else {
            sendXmlResponse(exception, exception.getErrorCode());
        }
    }

    @Override
    public void sendMultiStatus(MultiStatus multistatus) throws IOException {
        sendXmlResponse(multistatus, SC_MULTI_STATUS);
    }

    @Override
    public void sendMultiStatus(MultiStatus multistatus, List<String> acceptableContentCodings) throws IOException {
        sendXmlResponse(multistatus, SC_MULTI_STATUS, acceptableContentCodings);
    }

    /**
     * Send response body for a lock request that was intended to refresh one
     * or several locks.
     */
    public void sendRefreshLockResponse(ActiveLock[] locks) throws IOException {
        DavPropertySet propSet = new DavPropertySet();
        propSet.add(new LockDiscovery(locks));
        sendXmlResponse(propSet, SC_OK);
    }

    @Override
    public void sendXmlResponse(XmlSerializable serializable, int status) throws IOException {
        sendXmlResponse(serializable, status, Collections.emptyList());
    }

    @Override
    public void sendXmlResponse(XmlSerializable serializable, int status, List<String> acceptableContentCodings) throws IOException {
        httpResponse.setStatus(status);

        if (serializable != null) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                Document doc = createDocument();
                doc.appendChild(serializable.toXml(doc));
                DomUtil.transformDocument(doc, out);
                out.close();

                httpResponse.setContentType("text/xml; charset=UTF-8");

                // use GZIP iff accepted by client and content size >= 256 octets
                if (out.size() < 256 || !acceptableContentCodings.contains("gzip")) {
                    httpResponse.setContentLength(out.size());
                    out.writeTo(httpResponse.getOutputStream());
                } else {
                    httpResponse.setHeader("Content-Encoding", "gzip");
                    try (OutputStream os = new GZIPOutputStream(httpResponse.getOutputStream())) {
                        out.writeTo(os);
                    }
                }
            } catch (TransformerException | SAXException e) {
                log.error(e.getMessage());
                throw new IOException(e.getMessage());
            }
        }
    }

    //----------------------------< ObservationDavServletResponse Interface >---
    /**
     *
     * @param subscription
     * @throws IOException
     * @see org.apache.jackrabbit.webdav.observation.ObservationDavServletResponse#sendSubscriptionResponse(org.apache.jackrabbit.webdav.observation.Subscription)
     */
    public void sendSubscriptionResponse(Subscription subscription) throws IOException {
        String id = subscription.getSubscriptionId();
        if (id != null) {
            Header h = new CodedUrlHeader(ObservationConstants.HEADER_SUBSCRIPTIONID, id);
            httpResponse.setHeader(h.getHeaderName(), h.getHeaderValue());
        }
        DavPropertySet propSet = new DavPropertySet();
        propSet.add(new SubscriptionDiscovery(subscription));
        sendXmlResponse(propSet, SC_OK);
    }

    /**
     *
     * @param eventDiscovery
     * @throws IOException
     * @see org.apache.jackrabbit.webdav.observation.ObservationDavServletResponse#sendPollResponse(org.apache.jackrabbit.webdav.observation.EventDiscovery)
     */
    public void sendPollResponse(EventDiscovery eventDiscovery) throws IOException {
        sendXmlResponse(eventDiscovery, SC_OK);
    }

    //--------------------------------------< HttpServletResponse interface >---

    @Override
    public void addCookie(Cookie cookie) {
        httpResponse.addCookie(cookie);
    }

    @Override
    public boolean containsHeader(String s) {
        return httpResponse.containsHeader(s);
    }

    @Override
    public String encodeURL(String s) {
        return httpResponse.encodeRedirectURL(s);
    }

    @Override
    public String encodeRedirectURL(String s) {
        return httpResponse.encodeRedirectURL(s);
    }
    
    @Override
    public void sendError(int i, String s) throws IOException {
        httpResponse.sendError(i, s);
    }

    @Override
    public void sendError(int i) throws IOException {
        httpResponse.sendError(i);
    }

    @Override
    public void sendRedirect(String s) throws IOException {
        httpResponse.sendRedirect(s);
    }

    @Override
    public void setDateHeader(String s, long l) {
        httpResponse.setDateHeader(s, l);
    }

    @Override
    public void addDateHeader(String s, long l) {
        httpResponse.addDateHeader(s, l);
    }

    @Override
    public void setHeader(String s, String s1) {
        httpResponse.setHeader(s, s1);
    }

    @Override
    public void addHeader(String s, String s1) {
        httpResponse.addHeader(s, s1);
    }

    @Override
    public void setIntHeader(String s, int i) {
        httpResponse.setIntHeader(s, i);
    }

    @Override
    public void addIntHeader(String s, int i) {
        httpResponse.addIntHeader(s, i);
    }

    @Override
    public void setStatus(int i) {
        httpResponse.setStatus(i);
    }

    @Override
    public String getCharacterEncoding() {
        return httpResponse.getCharacterEncoding();
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return httpResponse.getOutputStream();
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        return httpResponse.getWriter();
    }

    @Override
    public void setContentLength(int i) {
        httpResponse.setContentLength(i);
    }

    @Override
    public void setContentType(String s) {
        httpResponse.setContentType(s);
    }

    @Override
    public void setBufferSize(int i) {
        httpResponse.setBufferSize(i);
    }

    @Override
    public int getBufferSize() {
        return httpResponse.getBufferSize();
    }

    @Override
    public void flushBuffer() throws IOException {
        httpResponse.flushBuffer();
    }

    @Override
    public void resetBuffer() {
        httpResponse.resetBuffer();
    }

    @Override
    public boolean isCommitted() {
        return httpResponse.isCommitted();
    }

    @Override
    public void reset() {
        httpResponse.reset();
    }

    @Override
    public void setLocale(Locale locale) {
        httpResponse.setLocale(locale);
    }

    @Override
    public Locale getLocale() {
        return httpResponse.getLocale();
    }

    @Override
    public String getContentType() {
        return httpResponse.getContentType();
    }

    @Override
    public void setCharacterEncoding(String charset) {
        httpResponse.setCharacterEncoding(charset);
    }

    @Override
    public int getStatus() {
        return httpResponse.getStatus();
    }

    @Override
    public String getHeader(String name) {
        return httpResponse.getHeader(name);
    }

    @Override
    public Collection<String> getHeaders(String name) {
        return httpResponse.getHeaders(name);
    }

    @Override
    public Collection<String> getHeaderNames() {
        return httpResponse.getHeaderNames();
    }

    @Override
    public void setContentLengthLong(long len) {
        httpResponse.setContentLengthLong(len);
    }

    // Servlet 4.0 API support for trailers, for now using reflection

    public void setTrailerFields(Supplier<Map<String, String>> supplier) {
        try {
            java.lang.reflect.Method stf = httpResponse.getClass().getDeclaredMethod("setTrailerFields", Supplier.class);
            stf.invoke(httpResponse, supplier);
        } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException | NoSuchMethodException
                 | SecurityException ex) {
            throw new UnsupportedOperationException("no servlet 4.0 support on: " + httpResponse.getClass(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    public Supplier<Map<String, String>> getTrailerFields() {
        try {
            java.lang.reflect.Method stf = httpResponse.getClass().getDeclaredMethod("getTrailerFields");
            return (Supplier<Map<String, String>>) stf.invoke(httpResponse);
        } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException | NoSuchMethodException
                 | SecurityException ex) {
            throw new UnsupportedOperationException("no servlet 4.0 support on: " + httpResponse.getClass(), ex);
        }
    }
}