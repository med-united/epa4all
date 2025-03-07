package de.servicehealth.epa4all.server.jcr.webdav.request.header;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;

/**
 * <code>DepthHeader</code>...
 */
@SuppressWarnings("JavadocDeclaration")
public class DepthHeader implements Header, DavConstants {

    private static final Logger log = LoggerFactory.getLogger(DepthHeader.class);

    private final int depth;

    /**
     * Create a new <code>DepthHeader</code> from the given integer.
     *
     * @param depth
     */
    public DepthHeader(int depth) {
        if (depth == DEPTH_0 || depth == DEPTH_1 || depth == DEPTH_INFINITY) {
            this.depth = depth;
        } else {
            throw new IllegalArgumentException("Invalid depth: " + depth);
        }
    }

    /**
     * Create a new <code>DepthHeader</code> with either value {@link #DEPTH_0 0}
     * or {@link #DEPTH_INFINITY infinity}.
     *
     * @param isDeep
     */
    public DepthHeader(boolean isDeep) {
        this.depth = (isDeep) ? DEPTH_INFINITY : DEPTH_0;
    }

    /**
     * @return integer representation of the depth indicated by the given header.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * Return {@link DavConstants#HEADER_DEPTH Depth}
     *
     * @return {@link DavConstants#HEADER_DEPTH Depth}
     * @see DavConstants#HEADER_DEPTH
     * @see Header#getHeaderName()
     */
    public String getHeaderName() {
        return HEADER_DEPTH;
    }

    /**
     * Returns the header value.
     *
     * @return header value
     * @see Header#getHeaderValue()
     */
    public String getHeaderValue() {
        if (depth == DEPTH_0 || depth == DEPTH_1) {
            return String.valueOf(depth);
        } else {
            return DEPTH_INFINITY_S;
        }
    }

    /**
     * Retrieve the Depth header from the given request object and parse the
     * value. If no header is present or the value is empty String, the
     * defaultValue is used ot build a new <code>DepthHeader</code> instance.
     *
     * @param request
     * @param defaultValue
     * @return a new <code>DepthHeader</code> instance
     */
    public static org.apache.jackrabbit.webdav.header.DepthHeader parse(HttpServletRequest request, int defaultValue) {
        String headerValue = request.getHeader(HEADER_DEPTH);
        if (headerValue == null || "".equals(headerValue)) {
            return new org.apache.jackrabbit.webdav.header.DepthHeader(defaultValue);
        } else {
            return new org.apache.jackrabbit.webdav.header.DepthHeader(depthToInt(headerValue));
        }
    }

    /**
     * Convert the String depth value to an integer.
     *
     * @param depth
     * @return integer representation of the given depth String
     * @throws IllegalArgumentException if the String does not represent a valid
     * depth.
     */
    private static int depthToInt(String depth) {
        int d;
        if (depth.equalsIgnoreCase(DEPTH_INFINITY_S)) {
            d = DEPTH_INFINITY;
        } else if (depth.equals(DEPTH_0 +"")) {
            d = DEPTH_0;
        } else if (depth.equals(DEPTH_1 +"")) {
            d = DEPTH_1;
        } else {
            throw new IllegalArgumentException("Invalid depth value: " + depth);
        }
        return d;
    }
}
