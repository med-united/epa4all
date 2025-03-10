package de.servicehealth.epa4all.server.jcr.webdav;

import de.servicehealth.epa4all.server.jcr.webdav.response.JDavServletResponse;

public interface JCRParams {

    /** the 'missing-auth-mapping' init parameter */
    String INIT_PARAM_MISSING_AUTH_MAPPING = "missing-auth-mapping";

    /**
     * Name of the optional init parameter that defines the value of the
     * 'WWW-Authenticate' header. If the parameter is omitted the default value
     * {@link #DEFAULT_AUTHENTICATE_HEADER "Basic Realm=Jackrabbit Webdav Server"}
     * is used.
     */
    String INIT_PARAM_AUTHENTICATE_HEADER = "authenticate-header";

    /**
     * Default value for the 'WWW-Authenticate' header, that is set, if request
     * results in a {@link JDavServletResponse#SC_UNAUTHORIZED 401 (Unauthorized)}
     * error.
     */
    String DEFAULT_AUTHENTICATE_HEADER = "Basic realm=\"Jackrabbit Webdav Server\"";

    /**
     * Name of the parameter that specifies the configuration of the CSRF protection.
     * May contain a comma-separated list of allowed referrer hosts.
     * If the parameter is omitted or left empty the behaviour is to only allow requests which have an empty referrer
     * or a referrer host equal to the server host.
     * If the parameter is set to 'disabled' no referrer checks will be performed at all.
     */
    String INIT_PARAM_CSRF_PROTECTION = "csrf-protection";

    /**
     * Name of the 'createAbsoluteURI' init parameter that defines whether hrefs
     * should be created with a absolute URI or as absolute Path (ContextPath).
     * The value should be 'true' or 'false'. The default value if not set is true.
     */
    String INIT_PARAM_CREATE_ABSOLUTE_URI = "createAbsoluteURI";

    /**
     * Init parameter specifying the prefix used with the resource path.
     */
    String INIT_PARAM_RESOURCE_PATH_PREFIX = "resource-path-prefix";

    /**
     * Optional 'concurrency-level' parameter defining the concurrency level
     * within the jcr-server. If the parameter is omitted the internal default
     * value (50) is used.
     */
    String INIT_PARAM_CONCURRENCY_LEVEL = "concurrency-level";

    /**
     * Servlet context attribute used to store the path prefix instead of
     * having a static field with this servlet. The latter causes problems
     * when running multiple
     */
    String CTX_ATTR_RESOURCE_PATH_PREFIX = "jackrabbit.webdav.jcr.resourcepath";
}