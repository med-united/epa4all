package de.servicehealth.epa4all.server.jcr.webdav.request.util;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static jakarta.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

/**
 * <code>CSRFUtil</code>...
 */
public class CSRFUtil {

    /**
     * Constant used to
     */
    public static final String DISABLED = "disabled";

    /**
     * Request content types for CSRF checking, see JCR-3909, JCR-4002, and JCR-4009
     */
    public static final Set<String> CONTENT_TYPES = Set.of(
        APPLICATION_FORM_URLENCODED, MULTIPART_FORM_DATA, TEXT_PLAIN
    );

    /**
     * logger instance
     */
    private static final Logger log = LoggerFactory.getLogger(org.apache.jackrabbit.webdav.util.CSRFUtil.class);

    /**
     * Disable referrer based CSRF protection
     */
    private final boolean disabled;

    /**
     * Additional allowed referrer hosts for CSRF protection
     */
    private final Set<String> allowedReferrerHosts;

    /**
     * Creates a new instance from the specified configuration, which defines
     * the behaviour of the referrer based CSRF protection as follows:
     * <ol>
     * <li>If config is <code>null</code> or empty string the default
     * behaviour is to allow only requests with an empty referrer header or a
     * referrer host equal to the server host</li>
     * <li>A comma separated list of additional allowed referrer hosts which are
     * valid in addition to default behaviour (see above).</li>
     * <li>The value {@link #DISABLED} may be used to disable the referrer checking altogether</li>
     * </ol>
     *
     * @param config The configuration value which may be any of the following:
     * <ul>
     * <li><code>null</code> or empty string for the default behaviour, which
     * only allows requests with an empty referrer header or a
     * referrer host equal to the server host</li>
     * <li>A comma separated list of additional allowed referrer hosts which are
     * valid in addition to default behaviour (see above).</li>
     * <li>{@link #DISABLED} in order to disable the referrer checking altogether</li>
     * </ul>
     */
    public CSRFUtil(String config) {
        if (config == null || config.isEmpty()) {
            disabled = false;
            allowedReferrerHosts = Collections.emptySet();
            log.debug("CSRF protection disabled");
        } else {
            if (DISABLED.equalsIgnoreCase(config.trim())) {
                disabled = true;
                allowedReferrerHosts = Collections.emptySet();
            } else {
                disabled = false;
                String[] allowed = config.split(",");
                allowedReferrerHosts = new HashSet<>(allowed.length);
                for (String entry : allowed) {
                    allowedReferrerHosts.add(entry.trim());
                }
            }
            log.debug("CSRF protection enabled, allowed referrers: " + allowedReferrerHosts);
        }
    }

    public boolean isValidRequest(HttpServletRequest request) {
        if (disabled) {
            return true;
        } else if (!"POST".equals(request.getMethod())) {
            // protection only needed for POST
            return true;
        } else {
            Enumeration<String> cts = request.getHeaders("Content-Type");
            String ct = null;
            if (cts != null && cts.hasMoreElements()) {
                String t = cts.nextElement();
                // prune parameters
                int semicolon = t.indexOf(';');
                if (semicolon >= 0) {
                    t = t.substring(0, semicolon);
                }
                ct = t.trim().toLowerCase(Locale.ENGLISH);
            }
            if (cts != null && cts.hasMoreElements()) {
                // reject if there are more header field instances
                log.debug("request blocked because there were multiple content-type header fields");
                return false;
            }
            if (ct != null && !CONTENT_TYPES.contains(ct)) {
                // type present and not in blacklist
                return true;
            }

            String refHeader = request.getHeader("Referer");
            // empty referrer headers are not allowed for POST + relevant
            // content types (see JCR-3909)
            if (refHeader == null) {
                log.debug("POST with content type " + ct + " blocked due to missing referer header field");
                return false;
            }

            try {
                String host = new URI(refHeader).getHost();
                // test referrer-host equals server or
                // if it is contained in the set of explicitly allowed host
                // names
                boolean ok = host == null || host.equals(request.getServerName()) || allowedReferrerHosts.contains(host);
                if (!ok) {
                    log.debug("POST with content type " + ct + " blocked due to referer header field being: " + refHeader);
                }
                return ok;
            } catch (URISyntaxException ex) {
                // referrer malformed -> block access
                log.debug("POST with content type " + ct + " blocked due to malformed referer header field: " + refHeader);
                return false;
            }
        }
    }
}