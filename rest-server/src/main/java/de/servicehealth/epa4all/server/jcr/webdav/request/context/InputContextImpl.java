package de.servicehealth.epa4all.server.jcr.webdav.request.context;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.io.InputContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.Date;

/**
 * <code>InputContextImpl</code> class encapsulates the <code>InputStream</code>
 * and some header values as present in the POST, PUT or MKCOL request.
 */
public class InputContextImpl implements InputContext {

    private static final Logger log = LoggerFactory.getLogger(InputContextImpl.class);

    private final HttpServletRequest request;
    private final InputStream in;

    public InputContextImpl(HttpServletRequest request, InputStream in) {
        if (request == null) {
            throw new IllegalArgumentException("DavResource and Request must not be null.");
        }

        this.request = request;
        this.in = in;
    }

    public boolean hasStream() {
        return in != null;
    }

    /**
     * Returns the input stream of the resource to import.
     *
     * @return the input stream.
     */
    public InputStream getInputStream() {
        return in;
    }

    public long getModificationTime() {
        return new Date().getTime();
    }

    /**
     * Returns the content language or <code>null</code>.
     *
     * @return contentLanguage
     */
    public String getContentLanguage() {
        return request.getHeader(DavConstants.HEADER_CONTENT_LANGUAGE);
    }

    /**
     * @return content length or -1 when unknown
     */
    public long getContentLength() {
        String length = request.getHeader(DavConstants.HEADER_CONTENT_LENGTH);
        if (length == null) {
            // header not present
            return -1;
        } else {
            try {
                return Long.parseLong(length);
            } catch (NumberFormatException ex) {
                log.error("broken Content-Length header: " + length);
                return -1;
            }
        }
    }

    public String getContentType() {
        return request.getHeader(DavConstants.HEADER_CONTENT_TYPE);
    }

    public String getProperty(String propertyName) {
        return request.getHeader(propertyName);
    }
}