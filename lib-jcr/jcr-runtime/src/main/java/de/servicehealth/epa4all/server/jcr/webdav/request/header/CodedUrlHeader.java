package de.servicehealth.epa4all.server.jcr.webdav.request.header;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.jackrabbit.webdav.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>CodedUrlHeader</code>...
 */
@SuppressWarnings("JavadocDeclaration")
public class CodedUrlHeader implements Header {

    private final String headerName;
    private final String headerValue;

    public CodedUrlHeader(String headerName, String headerValue) {
        this.headerName = headerName;
        if (headerValue != null && !(headerValue.startsWith("<") && headerValue.endsWith(">"))) {
            headerValue = "<" + headerValue + ">";
        }
        this.headerValue = headerValue;
    }

    /**
     * Return the name of the header
     *
     * @return header name
     * @see Header#getHeaderName()
     */
    public String getHeaderName() {
        return headerName;
    }

    /**
     * Return the value of the header
     *
     * @return value
     * @see Header#getHeaderValue()
     */
    public String getHeaderValue() {
        return headerValue;
    }

    /**
     * Returns the token present in the header value or <code>null</code>.
     * If the header contained multiple tokens separated by ',' the first value
     * is returned.
     *
     * @return token present in the CodedURL header or <code>null</code> if
     * the header is not present.
     * @see #getCodedUrls()
     */
    public String getCodedUrl() {
        String[] codedUrls = getCodedUrls();
        return (codedUrls != null) ? codedUrls[0] : null;
    }

    /**
     * Return an array of coded urls as present in the header value or <code>null</code> if
     * no value is present.
     *
     * @return array of coded urls
     */
    public String[] getCodedUrls() {
        String[] codedUrls = null;
        if (headerValue != null) {
            String[] values = headerValue.split(",");
            codedUrls = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                int p1 = values[i].indexOf('<');
                if (p1<0) {
                    throw new IllegalArgumentException("Invalid CodedURL header value:" + values[i]);
                }
                int p2 = values[i].indexOf('>', p1);
                if (p2<0) {
                    throw new IllegalArgumentException("Invalid CodedURL header value:" + values[i]);
                }
                codedUrls[i] = values[i].substring(p1+1, p2);
            }
        }
        return codedUrls;
    }

    /**
     * Retrieves the header with the given name and builds a new <code>CodedUrlHeader</code>.
     *
     * @param request
     * @param headerName
     * @return new <code>CodedUrlHeader</code> instance
     */
    public static CodedUrlHeader parse(HttpServletRequest request, String headerName) {
        String headerValue = request.getHeader(headerName);
        return new CodedUrlHeader(headerName, headerValue);
    }
}