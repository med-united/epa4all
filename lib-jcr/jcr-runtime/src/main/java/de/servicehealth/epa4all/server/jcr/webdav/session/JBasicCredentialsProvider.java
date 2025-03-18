package de.servicehealth.epa4all.server.jcr.webdav.session;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.jackrabbit.util.Base64;
import org.apache.jackrabbit.webdav.DavConstants;

import javax.jcr.Credentials;
import javax.jcr.GuestCredentials;
import javax.jcr.LoginException;
import javax.jcr.SimpleCredentials;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

@SuppressWarnings("JavadocDeclaration")
public class JBasicCredentialsProvider implements JCredentialsProvider {

    public static final String EMPTY_DEFAULT_HEADER_VALUE = "";
    public static final String GUEST_DEFAULT_HEADER_VALUE = "guestcredentials";

    private final String defaultHeaderValue;

    /**
     * Constructs a new BasicCredentialsProvider with the given default
     * value. See {@link #getCredentials} for details.
     *
     * @param defaultHeaderValue
     */
    public JBasicCredentialsProvider(String defaultHeaderValue) {
        this.defaultHeaderValue = defaultHeaderValue;
    }

    /**
     * {@inheritDoc}
     *
     * Build a {@link Credentials} object for the given authorization header.
     * The creds may be used to login to the repository. If the specified header
     * string is <code>null</code> the behaviour depends on the
     * {@link #defaultHeaderValue} field:<br>
     * <ul>
     * <li> if this field is <code>null</code>, a LoginException is thrown.
     *      This is suitable for clients (eg. webdav clients) for with
     *      sending a proper authorization header is not possible, if the
     *      server never send a 401.
     * <li> if this an empty string, null-credentials are returned, thus
     *      forcing an null login on the repository
     * <li> if this field has a 'user:password' value, the respective
     *      simple credentials are generated.
     * </ul>
     * <p>
     * If the request header is present but cannot be parsed a
     * <code>ServletException</code> is thrown.
     *
     * @param request the servlet request
     * @return credentials or <code>null</code>.
     * @throws ServletException If the Authorization header cannot be decoded.
     * @throws LoginException if no suitable auth header and missing-auth-mapping
     *         is not present
     */
    @Override
    public Credentials getCredentials(HttpServletRequest request)
        throws LoginException, ServletException {
        try {
            String authHeader = request.getHeader(DavConstants.HEADER_AUTHORIZATION);
            if (authHeader != null) {
                String[] authStr = authHeader.split(" ");
                if (authStr.length >= 2 && authStr[0].equalsIgnoreCase(HttpServletRequest.BASIC_AUTH)) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    Base64.decode(authStr[1].toCharArray(), out);
                    String decAuthStr = out.toString(ISO_8859_1);
                    int pos = decAuthStr.indexOf(':');
                    String userid = decAuthStr.substring(0, pos);
                    String passwd = decAuthStr.substring(pos + 1);
                    return new SimpleCredentials(userid, passwd.toCharArray());
                }
                throw new ServletException("Unable to decode authorization.");
            } else {
                // check special handling
                if (defaultHeaderValue == null) {
                    throw new LoginException();
                } else if (EMPTY_DEFAULT_HEADER_VALUE.equals(defaultHeaderValue)) {
                    return null;
                } else if (GUEST_DEFAULT_HEADER_VALUE.equals(defaultHeaderValue)) {
                    return new GuestCredentials();
                } else {
                    int pos = defaultHeaderValue.indexOf(':');
                    if (pos < 0) {
                        return new SimpleCredentials(defaultHeaderValue, new char[0]);
                    } else {
                        return new SimpleCredentials(
                            defaultHeaderValue.substring(0, pos),
                            defaultHeaderValue.substring(pos+1).toCharArray()
                        );
                    }
                }
            }
        } catch (IOException e) {
            throw new ServletException("Unable to decode authorization: " + e.toString());
        }
    }
}