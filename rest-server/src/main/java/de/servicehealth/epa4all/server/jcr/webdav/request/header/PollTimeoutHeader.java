package de.servicehealth.epa4all.server.jcr.webdav.request.header;

import org.apache.jackrabbit.webdav.observation.ObservationConstants;

import jakarta.servlet.http.HttpServletRequest;

/**
 * <code>PollTimeoutHeader</code> implements a timeout header for subscription
 * polling.
 */
@SuppressWarnings("JavadocDeclaration")
public class PollTimeoutHeader extends TimeoutHeader {

    public PollTimeoutHeader(long timeout) {
        super(timeout);
    }

    @Override
    public String getHeaderName() {
        return ObservationConstants.HEADER_POLL_TIMEOUT;
    }

    /**
     * Parses the request timeout header and converts it into a new
     * <code>PollTimeoutHeader</code> object.<br>The default value is used as
     * fallback if the String is not parseable.
     *
     * @param request
     * @param defaultValue
     * @return a new PollTimeoutHeader object.
     */
    public static PollTimeoutHeader parseHeader(HttpServletRequest request, long defaultValue) {
        String timeoutStr = request.getHeader(ObservationConstants.HEADER_POLL_TIMEOUT);
        long timeout = parse(timeoutStr, defaultValue);
        return new PollTimeoutHeader(timeout);
    }
}
