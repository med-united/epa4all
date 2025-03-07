package de.servicehealth.epa4all.server.jcr.webdav.request.header;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>TimeoutHeader</code>...
 */
@SuppressWarnings("JavadocDeclaration")
public class TimeoutHeader implements Header, DavConstants {

    private static Logger log = LoggerFactory.getLogger(TimeoutHeader.class);

    private final long timeout;

    public TimeoutHeader(long timeout) {
        this.timeout = timeout;
    }

    public String getHeaderName() {
        return HEADER_TIMEOUT;
    }

    public String getHeaderValue() {
        if (timeout == INFINITE_TIMEOUT) {
            return TIMEOUT_INFINITE;
        } else {
            return "Second-" + (timeout / 1000);
        }
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * Parses the request timeout header and converts it into a new
     * <code>TimeoutHeader</code> object.<br>The default value is used as
     * fallback if the String is not parseable.
     *
     * @param request
     * @param defaultValue
     * @return a new TimeoutHeader object.
     */
    public static TimeoutHeader parse(HttpServletRequest request, long defaultValue) {
        String timeoutStr = request.getHeader(HEADER_TIMEOUT);
        long timeout = parse(timeoutStr, defaultValue);
        return new TimeoutHeader(timeout);
    }

    /**
     * Parses the given timeout String and converts the timeout value
     * into a long indicating the number of milliseconds until expiration time
     * is reached.<br>
     * NOTE: If the timeout String equals to {@link #TIMEOUT_INFINITE 'infinite'}
     * {@link Integer#MAX_VALUE} is returned. If the Sting is invalid or is in an
     * invalid format that cannot be parsed, the default value is returned.
     *
     * @param timeoutStr
     * @param defaultValue
     * @return long representing the timeout present in the header or the default
     * value if the header is missing or could not be parsed.
     */
    public static long parse(String timeoutStr, long defaultValue) {
        long timeout = defaultValue;
        if (timeoutStr != null && timeoutStr.length() > 0) {
            int secondsInd = timeoutStr.indexOf("Second-");
            if (secondsInd >= 0) {
                secondsInd += 7; // read over "Second-"
                int i = secondsInd;
                while (i < timeoutStr.length() && Character.isDigit(timeoutStr.charAt(i))) {
                    i++;
                }
                try {
                    timeout = 1000L * Long.parseLong(timeoutStr.substring(secondsInd, i));
                } catch (NumberFormatException ignore) {
                    // ignore and return 'undefined' timeout
                    log.error("Invalid timeout format: " + timeoutStr);
                }
            } else if (timeoutStr.equalsIgnoreCase(TIMEOUT_INFINITE)) {
                timeout = INFINITE_TIMEOUT;
            }
        }
        return timeout;
    }
}
