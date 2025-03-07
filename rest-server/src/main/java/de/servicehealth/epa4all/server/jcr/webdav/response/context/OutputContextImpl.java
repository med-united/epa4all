package de.servicehealth.epa4all.server.jcr.webdav.response.context;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.io.OutputContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;

/**
 * <code>OutputContextImpl</code>...
 */
public class OutputContextImpl implements OutputContext {

    private static final Logger log = LoggerFactory.getLogger(OutputContextImpl.class);

    private final HttpServletResponse response;
    private final OutputStream out;

    public OutputContextImpl(HttpServletResponse response, OutputStream out) {
        if (response == null) {
            throw new IllegalArgumentException("Response must not be null.");
        }

        this.response = response;
        this.out = out;
    }

    public boolean hasStream() {
        return out != null;
    }

    public OutputStream getOutputStream() {
        return out;
    }

    public void setContentLanguage(String contentLanguage) {
        if (contentLanguage != null) {
            response.setHeader(DavConstants.HEADER_CONTENT_LANGUAGE, contentLanguage);
        }
    }

    public void setContentLength(long contentLength) {
        if (contentLength >= 0) {
            response.setContentLengthLong(contentLength);
        } // else: negative content length -> ignore.
    }

    public void setContentType(String contentType) {
        if (contentType != null) {
            response.setContentType(contentType);
        }
    }

    public void setModificationTime(long modificationTime) {
        if (modificationTime >= 0) {
            response.addDateHeader(DavConstants.HEADER_LAST_MODIFIED, modificationTime);
        }
    }

    public void setETag(String etag) {
        if (etag != null) {
            response.setHeader(DavConstants.HEADER_ETAG, etag);
        }
    }

    public void setProperty(String propertyName, String propertyValue) {
        if (propertyName != null && propertyValue != null) {
            response.setHeader(propertyName, propertyValue);
        }
    }
}