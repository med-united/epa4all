package de.servicehealth.epa4all.server.jcr.webdav.request.header;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.header.Header;

/**
 * <code>OverwriteHeader</code>...
 */
@SuppressWarnings("JavadocDeclaration")
public class OverwriteHeader implements Header {

    public static final String OVERWRITE_TRUE = "T";
    public static final String OVERWRITE_FALSE = "F";

    /**
     * Set 'doOverwrite' to <code>true</code> by default. See RFC 2518:
     * "If the overwrite header is not included in a COPY or MOVE request then
     * the resource MUST treat the request as if it has an overwrite header of
     * value {@link #OVERWRITE_TRUE}".
     */
    private final boolean doOverwrite;

    public OverwriteHeader(boolean doOverwrite) {
        this.doOverwrite = doOverwrite;
    }

    /**
     * Create a new <code>OverwriteHeader</code> for the given request object.
     * If the latter does not contain an "Overwrite" header field, the default
     * applies, which is {@link #OVERWRITE_TRUE} according to RFC 2518.
     *
     * @param request
     */
    public OverwriteHeader(HttpServletRequest request) {
        String overwriteHeader = request.getHeader(DavConstants.HEADER_OVERWRITE);
        if (overwriteHeader != null) {
            doOverwrite = overwriteHeader.equalsIgnoreCase(OVERWRITE_TRUE);
        } else {
            // no Overwrite header -> default is 'true'
            doOverwrite = true;
        }
    }

    public String getHeaderName() {
        return DavConstants.HEADER_OVERWRITE;
    }

    public String getHeaderValue() {
        return (doOverwrite) ? OVERWRITE_TRUE : OVERWRITE_FALSE;
    }

    public boolean isOverwrite() {
        return doOverwrite;
    }
}