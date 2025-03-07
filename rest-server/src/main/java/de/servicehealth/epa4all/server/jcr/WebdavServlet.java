package de.servicehealth.epa4all.server.jcr;

import de.servicehealth.epa4all.server.jcr.webdav.JDavMethods;
import de.servicehealth.epa4all.server.jcr.webdav.request.JDavServletRequest;
import de.servicehealth.epa4all.server.jcr.webdav.request.JWebdavRequest;
import de.servicehealth.epa4all.server.jcr.webdav.request.JWebdavRequestImpl;
import de.servicehealth.epa4all.server.jcr.webdav.request.context.InputContextImpl;
import de.servicehealth.epa4all.server.jcr.webdav.request.context.JWebdavRequestContextHolder;
import de.servicehealth.epa4all.server.jcr.webdav.request.context.JWebdavRequestContextImpl;
import de.servicehealth.epa4all.server.jcr.webdav.request.util.CSRFUtil;
import de.servicehealth.epa4all.server.jcr.webdav.resource.JDavResourceFactoryImpl;
import de.servicehealth.epa4all.server.jcr.webdav.response.JDavServletResponse;
import de.servicehealth.epa4all.server.jcr.webdav.response.JWebdavResponse;
import de.servicehealth.epa4all.server.jcr.webdav.response.JWebdavResponseImpl;
import de.servicehealth.epa4all.server.jcr.webdav.response.context.OutputContextImpl;
import de.servicehealth.epa4all.server.jcr.webdav.server.JCRWebdavServer;
import de.servicehealth.epa4all.server.jcr.webdav.server.JDavSessionProvider;
import de.servicehealth.epa4all.server.jcr.webdav.session.JBasicCredentialsProvider;
import de.servicehealth.epa4all.server.jcr.webdav.session.JCredentialsProvider;
import de.servicehealth.epa4all.server.jcr.webdav.session.JSessionProvider;
import de.servicehealth.epa4all.server.jcr.webdav.session.JSessionProviderImpl;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebInitParam;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jackrabbit.webdav.ContentCodingAwareRequest;
import org.apache.jackrabbit.webdav.DavCompliance;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavLocatorFactory;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.bind.BindInfo;
import org.apache.jackrabbit.webdav.bind.BindableResource;
import org.apache.jackrabbit.webdav.bind.RebindInfo;
import org.apache.jackrabbit.webdav.bind.UnbindInfo;
import org.apache.jackrabbit.webdav.header.CodedUrlHeader;
import org.apache.jackrabbit.webdav.io.InputContext;
import org.apache.jackrabbit.webdav.io.OutputContext;
import org.apache.jackrabbit.webdav.jcr.DavLocatorFactoryImpl;
import org.apache.jackrabbit.webdav.jcr.JcrDavSession;
import org.apache.jackrabbit.webdav.jcr.observation.SubscriptionManagerImpl;
import org.apache.jackrabbit.webdav.jcr.transaction.TxLockManagerImpl;
import org.apache.jackrabbit.webdav.lock.ActiveLock;
import org.apache.jackrabbit.webdav.lock.LockDiscovery;
import org.apache.jackrabbit.webdav.lock.LockInfo;
import org.apache.jackrabbit.webdav.observation.EventDiscovery;
import org.apache.jackrabbit.webdav.observation.ObservationResource;
import org.apache.jackrabbit.webdav.observation.Subscription;
import org.apache.jackrabbit.webdav.observation.SubscriptionInfo;
import org.apache.jackrabbit.webdav.observation.SubscriptionManager;
import org.apache.jackrabbit.webdav.ordering.OrderPatch;
import org.apache.jackrabbit.webdav.ordering.OrderingResource;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.PropEntry;
import org.apache.jackrabbit.webdav.search.SearchConstants;
import org.apache.jackrabbit.webdav.search.SearchInfo;
import org.apache.jackrabbit.webdav.search.SearchResource;
import org.apache.jackrabbit.webdav.security.AclProperty;
import org.apache.jackrabbit.webdav.security.AclResource;
import org.apache.jackrabbit.webdav.transaction.TransactionInfo;
import org.apache.jackrabbit.webdav.transaction.TransactionResource;
import org.apache.jackrabbit.webdav.util.HttpDateTimeFormatter;
import org.apache.jackrabbit.webdav.version.ActivityResource;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.DeltaVResource;
import org.apache.jackrabbit.webdav.version.LabelInfo;
import org.apache.jackrabbit.webdav.version.MergeInfo;
import org.apache.jackrabbit.webdav.version.OptionsInfo;
import org.apache.jackrabbit.webdav.version.OptionsResponse;
import org.apache.jackrabbit.webdav.version.UpdateInfo;
import org.apache.jackrabbit.webdav.version.VersionControlledResource;
import org.apache.jackrabbit.webdav.version.VersionResource;
import org.apache.jackrabbit.webdav.version.VersionableResource;
import org.apache.jackrabbit.webdav.version.report.Report;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.jcr.Repository;
import javax.jcr.Session;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serial;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_CONFLICT;
import static jakarta.servlet.http.HttpServletResponse.SC_CREATED;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_IMPLEMENTED;
import static jakarta.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static jakarta.servlet.http.HttpServletResponse.SC_PRECONDITION_FAILED;
import static jakarta.servlet.http.HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE;
import static org.apache.jackrabbit.webdav.DavConstants.DEPTH_0;
import static org.apache.jackrabbit.webdav.DavConstants.DEPTH_INFINITY;
import static org.apache.jackrabbit.webdav.DavConstants.HEADER_DESTINATION;
import static org.apache.jackrabbit.webdav.DavConstants.UNDEFINED_TIME;

// check VertxHttpServletRequest

@WebServlet(
    urlPatterns = "/webdav2/*",
    initParams = {
        @WebInitParam(name = "resource-path-prefix", value = "/webdav2"),
        @WebInitParam(name = "missing-auth-mapping", value = "admin:admin"),
        @WebInitParam(name = "repository-prefix", value = "repository")
    }
)
@ApplicationScoped
@SuppressWarnings({"JavadocDeclaration"})
public class WebdavServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = -1743484209610424817L;

    /**
     * the default logger
     */
    private static final Logger log = LoggerFactory.getLogger(WebdavServlet.class);

    /**
     * Init parameter specifying the prefix used with the resource path.
     */
    public static final String INIT_PARAM_RESOURCE_PATH_PREFIX = "resource-path-prefix";

    /**
     * Optional 'concurrency-level' parameter defining the concurrency level
     * within the jcr-server. If the parameter is omitted the internal default
     * value (50) is used.
     */
    public final static String INIT_PARAM_CONCURRENCY_LEVEL = "concurrency-level";

    /**
     * Servlet context attribute used to store the path prefix instead of
     * having a static field with this servlet. The latter causes problems
     * when running multiple
     */
    public static final String CTX_ATTR_RESOURCE_PATH_PREFIX = "jackrabbit.webdav.jcr.resourcepath";

    private String pathPrefix;

    private JCRWebdavServer server;
    private JDavResourceFactoryImpl resourceFactory;
    private DavLocatorFactory locatorFactory;
    protected TxLockManagerImpl txMgr;
    protected SubscriptionManager subscriptionMgr;

    @Inject
    RepositoryService repositoryService;

    public Repository getRepository() {
        return repositoryService.getRepository();
    }

    /** the 'missing-auth-mapping' init parameter */
    public final static String INIT_PARAM_MISSING_AUTH_MAPPING = "missing-auth-mapping";

    /**
     * Name of the optional init parameter that defines the value of the
     * 'WWW-Authenticate' header.
     * <p>
     * If the parameter is omitted the default value
     * {@link #DEFAULT_AUTHENTICATE_HEADER "Basic Realm=Jackrabbit Webdav Server"}
     * is used.
     *
     * @see #getAuthenticateHeaderValue()
     */
    public static final String INIT_PARAM_AUTHENTICATE_HEADER = "authenticate-header";

    /**
     * Default value for the 'WWW-Authenticate' header, that is set, if request
     * results in a {@link JDavServletResponse#SC_UNAUTHORIZED 401 (Unauthorized)}
     * error.
     *
     * @see #getAuthenticateHeaderValue()
     */
    public static final String DEFAULT_AUTHENTICATE_HEADER = "Basic realm=\"Jackrabbit Webdav Server\"";

    /**
     * Name of the parameter that specifies the configuration of the CSRF protection.
     * May contain a comma-separated list of allowed referrer hosts.
     * If the parameter is omitted or left empty the behaviour is to only allow requests which have an empty referrer
     * or a referrer host equal to the server host.
     * If the parameter is set to 'disabled' no referrer checks will be performed at all.
     */
    public static final String INIT_PARAM_CSRF_PROTECTION = "csrf-protection";

    /**
     * Name of the 'createAbsoluteURI' init parameter that defines whether hrefs
     * should be created with a absolute URI or as absolute Path (ContextPath).
     * The value should be 'true' or 'false'. The default value if not set is true.
     */
    public final static String INIT_PARAM_CREATE_ABSOLUTE_URI = "createAbsoluteURI";


    /**
     * Header value as specified in the {@link #INIT_PARAM_AUTHENTICATE_HEADER} parameter.
     */
    private String authenticate_header;

    /**
     * CSRF protection utility
     */
    private CSRFUtil csrfUtil;

    /**
     * Create per default absolute URI hrefs
     */
    private boolean createAbsoluteURI = true;

    @Override
    public void init() throws ServletException {
        super.init();

        // authenticate header
        authenticate_header = getInitParameter(INIT_PARAM_AUTHENTICATE_HEADER);
        if (authenticate_header == null) {
            authenticate_header = DEFAULT_AUTHENTICATE_HEADER;
        }
        log.info(INIT_PARAM_AUTHENTICATE_HEADER + " = " + authenticate_header);

        // read csrf protection params
        String csrfParam = getInitParameter(INIT_PARAM_CSRF_PROTECTION);
        csrfUtil = new CSRFUtil(csrfParam);
        log.info(INIT_PARAM_CSRF_PROTECTION + " = " + csrfParam);

        // create absolute URI hrefs
        String param = getInitParameter(INIT_PARAM_CREATE_ABSOLUTE_URI);
        if (param != null) {
            createAbsoluteURI = Boolean.parseBoolean(param);
        }
        log.info(INIT_PARAM_CREATE_ABSOLUTE_URI + " = " + createAbsoluteURI);

        // set resource path prefix
        pathPrefix = getInitParameter(INIT_PARAM_RESOURCE_PATH_PREFIX);
        getServletContext().setAttribute(CTX_ATTR_RESOURCE_PATH_PREFIX, pathPrefix);
        log.debug(INIT_PARAM_RESOURCE_PATH_PREFIX + " = " + pathPrefix);

        txMgr = new TxLockManagerImpl();
        subscriptionMgr = new SubscriptionManagerImpl();
        txMgr.addTransactionListener((SubscriptionManagerImpl) subscriptionMgr);

        // todo: eventually make configurable

        resourceFactory = new JDavResourceFactoryImpl(txMgr, subscriptionMgr);

        locatorFactory = new DavLocatorFactoryImpl(pathPrefix);
    }

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        JWebdavRequest webdavRequest = new JWebdavRequestImpl(request, getLocatorFactory(), isCreateAbsoluteURI());
        // DeltaV requires 'Cache-Control' header for all methods except 'VERSION-CONTROL' and 'REPORT'.
        int methodCode = DavMethods.getMethodCode(request.getMethod());
        boolean noCache = JDavMethods.isDeltaVMethod(webdavRequest) && !(DavMethods.DAV_VERSION_CONTROL == methodCode || DavMethods.DAV_REPORT == methodCode);
        JWebdavResponse webdavResponse = new JWebdavResponseImpl(response, noCache);

        try {
            JWebdavRequestContextHolder.setContext(new JWebdavRequestContextImpl(webdavRequest));

            // make sure there is a authenticated user
            if (!getDavSessionProvider().attachSession(webdavRequest)) {
                return;
            }

            // perform referrer host checks if CSRF protection is enabled
            if (!csrfUtil.isValidRequest(webdavRequest)) {
                webdavResponse.sendError(SC_FORBIDDEN);
                return;
            }

            // JCR-4165: reject any content-coding in request until we can
            // support it (see JCR-4166)
            if (!(webdavRequest instanceof ContentCodingAwareRequest)) {
                List<String> ces = getContentCodings(request);
                if (!ces.isEmpty()) {
                    webdavResponse.setStatus(SC_UNSUPPORTED_MEDIA_TYPE);
                    webdavResponse.setHeader("Accept-Encoding", "identity");
                    webdavResponse.setContentType("text/plain; charset=UTF-8");
                    webdavResponse.getWriter().println("Content-Encodings not supported, but received: " + ces);
                    webdavResponse.getWriter().flush();
                }
            }

            // check matching if=header for lock-token relevant operations
            DavResource resource = resourceFactory.createResource(
                webdavRequest.getRequestLocator(), webdavRequest, webdavResponse
            );
            if (!isPreconditionValid(webdavRequest, resource)) {
                webdavResponse.sendError(SC_PRECONDITION_FAILED);
                return;
            }
            if (!execute(webdavRequest, webdavResponse, methodCode, resource)) {
                super.service(request, response);
            }
        } catch (DavException e) {
            handleDavException(webdavRequest, webdavResponse, e);
        } catch (IOException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof DavException) {
                handleDavException(webdavRequest, webdavResponse, (DavException) cause);
            } else {
                throw ex;
            }
        } finally {
            JWebdavRequestContextHolder.clearContext();
            getDavSessionProvider().releaseSession(webdavRequest);
        }
    }

    /**
     * Returns true if the preconditions are met. This includes validation of
     * {@link JWebdavRequest#matchesIfHeader(DavResource) If header} and validation
     * of {@link org.apache.jackrabbit.webdav.transaction.TransactionConstants#HEADER_TRANSACTIONID
     * TransactionId header}. This method will also return false if the requested
     * resource resides within a different workspace as is assigned to the repository
     * session attached to the given request.
     */
    private boolean isPreconditionValid(JWebdavRequest request, DavResource resource) {
        // first check matching If header
        if (!request.matchesIfHeader(resource)) {
            return false;
        }

        // test if the requested path matches to the existing session
        // this may occur if the session was retrieved from the cache.
        try {
            Session repositorySesssion = JcrDavSession.getRepositorySession(request.getDavSession());
            String reqWspName = resource.getLocator().getWorkspaceName();
            String wsName = repositorySesssion.getWorkspace().getName();
            // compare workspace names if the requested resource isn't the
            // root-collection and the request not MKWORKSPACE.
            if (DavMethods.DAV_MKWORKSPACE != DavMethods.getMethodCode(request.getMethod()) &&
                reqWspName != null && !reqWspName.equals(wsName)) {
                return false;
            }
        } catch (DavException e) {
            log.error("Internal error: " + e.toString());
            return false;
        }


        // make sure, the TransactionId header is valid
        String txId = request.getTransactionId();
        return txId == null || txMgr.hasLock(txId, resource);
    }

    private JDavSessionProvider getDavSessionProvider() {
        if (server == null) {
            Repository repository = getRepository();
            String cl = getInitParameter(INIT_PARAM_CONCURRENCY_LEVEL);
            if (cl != null) {
                try {
                    server = new JCRWebdavServer(repository, getSessionProvider(), Integer.parseInt(cl));
                } catch (NumberFormatException e) {
                    log.debug("Invalid value '" + cl + "' for init-param 'concurrency-level'. Using default instead.");
                    server = new JCRWebdavServer(repository, getSessionProvider());
                }
            } else {
                server = new JCRWebdavServer(repository, getSessionProvider());
            }
        }
        return server;
    }

    private DavLocatorFactory getLocatorFactory() {
        if (locatorFactory == null) {
            locatorFactory = new DavLocatorFactoryImpl(pathPrefix);
        }
        return locatorFactory;
    }

    /**
     * Returns the configured path prefix
     *
     * @param ctx The servlet context.
     * @return resourcePathPrefix
     * @see #INIT_PARAM_RESOURCE_PATH_PREFIX
     */
    public static String getPathPrefix(ServletContext ctx) {
        return (String) ctx.getAttribute(CTX_ATTR_RESOURCE_PATH_PREFIX);
    }

    /**
     * Returns a new instanceof <code>BasicCredentialsProvider</code>.
     *
     * @return a new credentials provider
     */
    protected JCredentialsProvider getCredentialsProvider() {
        return new JBasicCredentialsProvider(getInitParameter(INIT_PARAM_MISSING_AUTH_MAPPING));
    }

    /**
     * Returns a new instanceof <code>SessionProviderImpl</code>.
     *
     * @return a new session provider
     */
    protected JSessionProvider getSessionProvider() {
        return new JSessionProviderImpl(getCredentialsProvider());
    }

    /**
     * Returns the value of the 'WWW-Authenticate' header, that is returned in
     * case of 401 error: the value is retrireved from the corresponding init
     * param or defaults to {@link #DEFAULT_AUTHENTICATE_HEADER}.
     *
     * @return corresponding init parameter or {@link #DEFAULT_AUTHENTICATE_HEADER}.
     * @see #INIT_PARAM_AUTHENTICATE_HEADER
     */
    public String getAuthenticateHeaderValue() {
        return authenticate_header;
    }

    /**
     * Returns if a absolute URI should be created for hrefs.
     *
     * @return absolute URI hrefs
     */
    protected boolean isCreateAbsoluteURI() {
        return createAbsoluteURI;
    }

    private void handleDavException(JWebdavRequest webdavRequest, JWebdavResponse webdavResponse, DavException ex)
        throws IOException {
        if (ex.getErrorCode() == HttpServletResponse.SC_UNAUTHORIZED) {
            sendUnauthorized(webdavResponse, ex);
        } else {
            Element condition = ex.getErrorCondition();
            if (DomUtil.matches(condition, ContentCodingAwareRequest.PRECONDITION_SUPPORTED)) {
                if (webdavRequest instanceof ContentCodingAwareRequest) {
                    webdavResponse.setHeader("Accept-Encoding", ((ContentCodingAwareRequest) webdavRequest).getAcceptableCodings());
                }
            }
            webdavResponse.sendError(ex);
        }
    }

    /**
     * If request payload was uncompressed, hint about acceptable content codings (RFC 7694)
     */
    private void addHintAboutPotentialRequestEncodings(JWebdavRequest webdavRequest, JWebdavResponse webdavResponse) {
        if (webdavRequest instanceof ContentCodingAwareRequest ccr) {
            List<String> ces = ccr.getRequestContentCodings();
            if (ces.isEmpty()) {
                webdavResponse.setHeader("Accept-Encoding", ccr.getAcceptableCodings());
            }
        }
    }

    /**
     * Sets the "WWW-Authenticate" header and writes the appropriate error
     * to the given webdav response.
     *
     * @param response The webdav response.
     * @param error    The DavException that leads to the unauthorized response.
     * @throws IOException
     */
    protected void sendUnauthorized(
        JWebdavResponse response,
        DavException error
    ) throws IOException {
        response.setHeader("WWW-Authenticate", getAuthenticateHeaderValue());
        if (error == null || error.getErrorCode() != HttpServletResponse.SC_UNAUTHORIZED) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            response.sendError(error.getErrorCode(), error.getStatusPhrase());
        }
    }

    /**
     * Executes the respective method in the given webdav context
     */
    protected boolean execute(JWebdavRequest request, JWebdavResponse response,
                              int method, DavResource resource)
        throws ServletException, IOException, DavException {

        switch (method) {
            case DavMethods.DAV_GET:
                doGet(request, response, resource);
                break;
            case DavMethods.DAV_HEAD:
                doHead(request, response, resource);
                break;
            case DavMethods.DAV_PROPFIND:
                doPropFind(request, response, resource);
                break;
            case DavMethods.DAV_PROPPATCH:
                doPropPatch(request, response, resource);
                break;
            case DavMethods.DAV_POST:
                doPost(request, response, resource);
                break;
            case DavMethods.DAV_PUT:
                doPut(request, response, resource);
                break;
            case DavMethods.DAV_DELETE:
                doDelete(request, response, resource);
                break;
            case DavMethods.DAV_COPY:
                doCopy(request, response, resource);
                break;
            case DavMethods.DAV_MOVE:
                doMove(request, response, resource);
                break;
            case DavMethods.DAV_MKCOL:
                doMkCol(request, response, resource);
                break;
            case DavMethods.DAV_OPTIONS:
                doOptions(request, response, resource);
                break;
            case DavMethods.DAV_LOCK:
                doLock(request, response, resource);
                break;
            case DavMethods.DAV_UNLOCK:
                doUnlock(request, response, resource);
                break;
            case DavMethods.DAV_ORDERPATCH:
                doOrderPatch(request, response, resource);
                break;
            case DavMethods.DAV_SUBSCRIBE:
                doSubscribe(request, response, resource);
                break;
            case DavMethods.DAV_UNSUBSCRIBE:
                doUnsubscribe(request, response, resource);
                break;
            case DavMethods.DAV_POLL:
                doPoll(request, response, resource);
                break;
            case DavMethods.DAV_SEARCH:
                doSearch(request, response, resource);
                break;
            case DavMethods.DAV_VERSION_CONTROL:
                doVersionControl(request, response, resource);
                break;
            case DavMethods.DAV_LABEL:
                doLabel(request, response, resource);
                break;
            case DavMethods.DAV_REPORT:
                doReport(request, response, resource);
                break;
            case DavMethods.DAV_CHECKIN:
                doCheckin(request, response, resource);
                break;
            case DavMethods.DAV_CHECKOUT:
                doCheckout(request, response, resource);
                break;
            case DavMethods.DAV_UNCHECKOUT:
                doUncheckout(request, response, resource);
                break;
            case DavMethods.DAV_MERGE:
                doMerge(request, response, resource);
                break;
            case DavMethods.DAV_UPDATE:
                doUpdate(request, response, resource);
                break;
            case DavMethods.DAV_MKWORKSPACE:
                doMkWorkspace(request, response, resource);
                break;
            case DavMethods.DAV_MKACTIVITY:
                doMkActivity(request, response, resource);
                break;
            case DavMethods.DAV_BASELINE_CONTROL:
                doBaselineControl(request, response, resource);
                break;
            case DavMethods.DAV_ACL:
                doAcl(request, response, resource);
                break;
            case DavMethods.DAV_REBIND:
                doRebind(request, response, resource);
                break;
            case DavMethods.DAV_UNBIND:
                doUnbind(request, response, resource);
                break;
            case DavMethods.DAV_BIND:
                doBind(request, response, resource);
                break;
            default:
                // any other method
                return false;
        }
        return true;
    }

    /**
     * The OPTION method
     *
     * @param request
     * @param response
     * @param resource
     */
    protected void doOptions(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws IOException, DavException {
        response.addHeader(DavConstants.HEADER_DAV, resource.getComplianceClass());
        response.addHeader("Allow", resource.getSupportedMethods());
        response.addHeader("MS-Author-Via", DavConstants.HEADER_DAV);
        if (resource instanceof SearchResource) {
            String[] langs = ((SearchResource) resource).getQueryGrammerSet().getQueryLanguages();
            for (String lang : langs) {
                response.addHeader(SearchConstants.HEADER_DASL, "<" + lang + ">");
            }
        }
        // with DeltaV the OPTIONS request may contain a Xml body.
        OptionsResponse oR = null;
        OptionsInfo oInfo = request.getOptionsInfo();
        if (oInfo != null && resource instanceof DeltaVResource) {
            oR = ((DeltaVResource) resource).getOptionResponse(oInfo);
        }
        if (oR == null) {
            response.setStatus(SC_OK);
        } else {
            response.sendXmlResponse(oR, SC_OK);
        }
    }

    /**
     * The HEAD method
     *
     * @param request
     * @param response
     * @param resource
     * @throws IOException
     */
    protected void doHead(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws IOException {
        spoolResource(request, response, resource, false);
    }

    /**
     * The GET method
     *
     * @param request
     * @param response
     * @param resource
     * @throws IOException
     */
    protected void doGet(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws IOException, DavException {
        spoolResource(request, response, resource, true);
    }

    /**
     * @param request
     * @param response
     * @param resource
     * @param sendContent
     * @throws IOException
     */
    private void spoolResource(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource,
        boolean sendContent
    ) throws IOException {

        if (!resource.exists()) {
            response.sendError(SC_NOT_FOUND);
            return;
        }

        long modSince = UNDEFINED_TIME;
        try {
            // will throw if multiple field lines present
            String value = getSingletonField(request, "If-Modified-Since");
            if (value != null) {
                modSince = HttpDateTimeFormatter.parse(value);
            }
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            log.debug("illegal value for if-modified-since ignored: " + ex.getMessage());
        }

        if (modSince > UNDEFINED_TIME) {
            long modTime = resource.getModificationTime();
            // test if resource has been modified. note that formatted modification
            // time lost the millisecond precision
            if (modTime != UNDEFINED_TIME && (modTime / 1000 * 1000) <= modSince) {
                // resource has not been modified since the time indicated in the
                // 'If-Modified-Since' header.

                DavProperty<?> etagProp = resource.getProperty(DavPropertyName.GETETAG);
                if (etagProp != null) {
                    // 304 response MUST contain Etag when available
                    response.setHeader("etag", etagProp.getValue().toString());
                }
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }
        }

        // spool resource properties and eventually resource content.
        OutputStream out = (sendContent) ? response.getOutputStream() : null;
        resource.spool(getOutputContext(response, out));
        response.flushBuffer();
    }

    /**
     * The PROPFIND method
     *
     * @param request
     * @param response
     * @param resource
     * @throws IOException
     */
    protected void doPropFind(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws IOException, DavException {

        if (!resource.exists()) {
            response.sendError(SC_NOT_FOUND);
            return;
        }

        int depth = request.getDepth(DEPTH_INFINITY);
        DavPropertyNameSet requestProperties = request.getPropFindProperties();
        int propfindType = request.getPropFindType();

        MultiStatus mstatus = new MultiStatus();
        mstatus.addResourceProperties(resource, requestProperties, propfindType, depth);

        addHintAboutPotentialRequestEncodings(request, response);

        response.sendMultiStatus(mstatus,
            acceptsGzipEncoding(request) ? Collections.singletonList("gzip") : Collections.emptyList());
    }

    /**
     * The PROPPATCH method
     *
     * @param request
     * @param response
     * @param resource
     * @throws IOException
     */
    protected void doPropPatch(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws IOException, DavException {

        List<? extends PropEntry> changeList = request.getPropPatchChangeList();
        if (changeList.isEmpty()) {
            response.sendError(SC_BAD_REQUEST);
            return;
        }

        MultiStatus ms = new MultiStatus();
        MultiStatusResponse msr = resource.alterProperties(changeList);
        ms.addResponse(msr);

        addHintAboutPotentialRequestEncodings(request, response);

        response.sendMultiStatus(ms);
    }

    /**
     * The POST method. Delegate to PUT
     *
     * @param request
     * @param response
     * @param resource
     * @throws IOException
     * @throws DavException
     */
    protected void doPost(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws IOException, DavException {
        response.sendError(SC_METHOD_NOT_ALLOWED);
    }

    /**
     * The PUT method
     *
     * @param request
     * @param response
     * @param resource
     * @throws IOException
     * @throws DavException
     */
    protected void doPut(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws IOException, DavException {

        if (request.getHeader("Content-Range") != null) {
            response.sendError(SC_BAD_REQUEST, "Content-Range in PUT request not supported");
            return;
        }

        DavResource parentResource = resource.getCollection();
        if (parentResource == null || !parentResource.exists()) {
            // parent does not exist
            response.sendError(SC_CONFLICT);
            return;
        }

        int status;
        // test if resource already exists
        if (resource.exists()) {
            status = SC_NO_CONTENT;
        } else {
            status = SC_CREATED;
        }

        parentResource.addMember(resource, getInputContext(request, request.getInputStream()));
        response.setStatus(status);
    }

    /**
     * The MKCOL method
     *
     * @param request
     * @param response
     * @param resource
     * @throws IOException
     * @throws DavException
     */
    protected void doMkCol(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws IOException, DavException {

        DavResource parentResource = resource.getCollection();
        if (parentResource == null || !parentResource.exists() || !parentResource.isCollection()) {
            // parent does not exist or is not a collection
            response.sendError(SC_CONFLICT);
            return;
        }
        // shortcut: mkcol is only allowed on deleted/non-existing resources
        if (resource.exists()) {
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }

        if (request.getContentLength() > 0 || request.getHeader("Transfer-Encoding") != null) {
            parentResource.addMember(resource, getInputContext(request, request.getInputStream()));
        } else {
            parentResource.addMember(resource, getInputContext(request, null));
        }
        response.setStatus(SC_CREATED);
    }

    /**
     * The DELETE method
     *
     * @param request
     * @param response
     * @param resource
     * @throws IOException
     * @throws DavException
     */
    protected void doDelete(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws IOException, DavException {
        DavResource parent = resource.getCollection();
        if (parent != null) {
            parent.removeMember(resource);
            response.setStatus(SC_NO_CONTENT);
        } else {
            response.sendError(SC_FORBIDDEN, "Cannot remove the root resource.");
        }
    }

    /**
     * The COPY method
     *
     * @param request
     * @param response
     * @param resource
     * @throws IOException
     * @throws DavException
     */
    protected void doCopy(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws IOException, DavException {

        // only depth 0 and infinity is allowed
        int depth = request.getDepth(DEPTH_INFINITY);
        if (!(depth == DEPTH_0 || depth == DEPTH_INFINITY)) {
            response.sendError(SC_BAD_REQUEST);
            return;
        }

        DavResource destResource = resourceFactory.createResource(request.getDestinationLocator(), request, response);
        int status = validateDestination(destResource, request, true);
        if (status > SC_NO_CONTENT) {
            response.sendError(status);
            return;
        }

        resource.copy(destResource, depth == DEPTH_0);
        response.setStatus(status);
    }

    /**
     * The MOVE method
     *
     * @param request
     * @param response
     * @param resource
     * @throws IOException
     * @throws DavException
     */
    protected void doMove(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws IOException, DavException {

        DavResource destResource = resourceFactory.createResource(request.getDestinationLocator(), request, response);
        int status = validateDestination(destResource, request, true);
        if (status > SC_NO_CONTENT) {
            response.sendError(status);
            return;
        }

        resource.move(destResource);
        response.setStatus(status);
    }

    /**
     * The BIND method
     *
     * @param request
     * @param response
     * @param resource the collection resource to which a new member will be added
     * @throws IOException
     * @throws DavException
     */
    protected void doBind(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws IOException, DavException {

        if (!resource.exists()) {
            response.sendError(SC_NOT_FOUND);
        }
        BindInfo bindInfo = request.getBindInfo();
        DavResource oldBinding = resourceFactory.createResource(request.getHrefLocator(bindInfo.getHref()), request, response);
        if (!(oldBinding instanceof BindableResource)) {
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }
        DavResource newBinding = resourceFactory.createResource(request.getMemberLocator(bindInfo.getSegment()), request, response);
        int status = validateDestination(newBinding, request, false);
        if (status > SC_NO_CONTENT) {
            response.sendError(status);
            return;
        }
        ((BindableResource) oldBinding).bind(resource, newBinding);
        response.setStatus(status);
    }

    /**
     * The REBIND method
     *
     * @param request
     * @param response
     * @param resource the collection resource to which a new member will be added
     * @throws IOException
     * @throws DavException
     */
    protected void doRebind(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws IOException, DavException {

        if (!resource.exists()) {
            response.sendError(SC_NOT_FOUND);
        }
        RebindInfo rebindInfo = request.getRebindInfo();
        DavResource oldBinding = resourceFactory.createResource(request.getHrefLocator(rebindInfo.getHref()), request, response);
        if (!(oldBinding instanceof BindableResource)) {
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }
        DavResource newBinding = resourceFactory.createResource(request.getMemberLocator(rebindInfo.getSegment()), request, response);
        int status = validateDestination(newBinding, request, false);
        if (status > SC_NO_CONTENT) {
            response.sendError(status);
            return;
        }
        ((BindableResource) oldBinding).rebind(resource, newBinding);
        response.setStatus(status);
    }

    /**
     * The UNBIND method
     *
     * @param request
     * @param response
     * @param resource the collection resource from which a member will be removed
     * @throws IOException
     * @throws DavException
     */
    protected void doUnbind(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws IOException, DavException {

        UnbindInfo unbindInfo = request.getUnbindInfo();
        DavResource srcResource = resourceFactory.createResource(request.getMemberLocator(unbindInfo.getSegment()), request, response);
        resource.removeMember(srcResource);
    }

    /**
     * Validate the given destination resource and return the proper status
     * code: Any return value greater/equal than {@link JDavServletResponse#SC_NO_CONTENT}
     * indicates an error.
     *
     * @param destResource destination resource to be validated.
     * @param request
     * @param checkHeader  flag indicating if the destination header must be present.
     * @return status code indicating whether the destination is valid.
     */
    protected int validateDestination(DavResource destResource, JWebdavRequest request, boolean checkHeader)
        throws DavException {

        if (checkHeader) {
            String destHeader = request.getHeader(HEADER_DESTINATION);
            if (destHeader == null || destHeader.isEmpty()) {
                return SC_BAD_REQUEST;
            }
        }
        if (destResource.getLocator().equals(request.getRequestLocator())) {
            return SC_FORBIDDEN;
        }

        int status;
        if (destResource.exists()) {
            if (request.isOverwrite()) {
                // matching if-header required for existing resources
                if (!request.matchesIfHeader(destResource)) {
                    return SC_PRECONDITION_FAILED;
                } else {
                    // overwrite existing resource
                    destResource.getCollection().removeMember(destResource);
                    status = SC_NO_CONTENT;
                }
            } else {
              /* NO overwrite header:

                 but, instead of return the 412 Precondition-Failed code required
                 by the WebDAV specification(s) leave the validation to the
                 JCR repository.
               */
                status = SC_CREATED;
            }

        } else {
            // destination does not exist >> copy/move can be performed
            status = SC_CREATED;
        }
        return status;
    }

    /**
     * The LOCK method
     *
     * @param request
     * @param response
     * @param resource
     * @throws IOException
     * @throws DavException
     */
    protected void doLock(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws IOException, DavException {

        LockInfo lockInfo = request.getLockInfo();
        if (lockInfo.isRefreshLock()) {
            // refresh any matching existing locks
            ActiveLock[] activeLocks = resource.getLocks();
            List<ActiveLock> lList = new ArrayList<ActiveLock>();
            for (ActiveLock activeLock : activeLocks) {
                // adjust lockinfo with type/scope retrieved from the lock.
                lockInfo.setType(activeLock.getType());
                lockInfo.setScope(activeLock.getScope());

                DavProperty<?> etagProp = resource.getProperty(DavPropertyName.GETETAG);
                String etag = etagProp != null ? String.valueOf(etagProp.getValue()) : "";
                if (request.matchesIfHeader(resource.getHref(), activeLock.getToken(), etag)) {
                    lList.add(resource.refreshLock(lockInfo, activeLock.getToken()));
                }
            }
            if (lList.isEmpty()) {
                throw new DavException(SC_PRECONDITION_FAILED);
            }
            ActiveLock[] refreshedLocks = lList.toArray(new ActiveLock[lList.size()]);
            response.sendRefreshLockResponse(refreshedLocks);
        } else {
            int status = SC_OK;
            if (!resource.exists()) {
                // lock-empty requires status code 201 (Created)
                status = SC_CREATED;
            }

            // create a new lock
            ActiveLock lock = resource.lock(lockInfo);

            CodedUrlHeader header = new CodedUrlHeader(
                DavConstants.HEADER_LOCK_TOKEN, lock.getToken());
            response.setHeader(header.getHeaderName(), header.getHeaderValue());

            DavPropertySet propSet = new DavPropertySet();
            propSet.add(new LockDiscovery(lock));
            response.sendXmlResponse(propSet, status);
        }
    }

    /**
     * The UNLOCK method
     *
     * @param request
     * @param response
     * @param resource
     * @throws DavException
     */
    protected void doUnlock(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws DavException {
        // get lock token from header
        String lockToken = request.getLockToken();
        TransactionInfo tInfo = request.getTransactionInfo();
        if (tInfo != null) {
            ((TransactionResource) resource).unlock(lockToken, tInfo);
        } else {
            resource.unlock(lockToken);
        }
        response.setStatus(SC_NO_CONTENT);
    }

    /**
     * The ORDERPATCH method
     *
     * @param request
     * @param response
     * @param resource
     * @throws IOException
     * @throws DavException
     */
    protected void doOrderPatch(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws IOException, DavException {

        if (!(resource instanceof OrderingResource)) {
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }

        OrderPatch op = request.getOrderPatch();
        if (op == null) {
            response.sendError(SC_BAD_REQUEST);
            return;
        }
        // perform reordering of internal members
        ((OrderingResource) resource).orderMembers(op);
        response.setStatus(SC_OK);
    }

    /**
     * The SUBSCRIBE method
     *
     * @param request
     * @param response
     * @param resource
     * @throws IOException
     * @throws DavException
     */
    protected void doSubscribe(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws IOException, DavException {

        if (!(resource instanceof ObservationResource)) {
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }

        SubscriptionInfo info = request.getSubscriptionInfo();
        if (info == null) {
            response.sendError(SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }
        Subscription subs = ((ObservationResource) resource).subscribe(info, request.getSubscriptionId());
        response.sendSubscriptionResponse(subs);
    }

    /**
     * The UNSUBSCRIBE method
     *
     * @param request
     * @param response
     * @param resource
     * @throws IOException
     * @throws DavException
     */
    protected void doUnsubscribe(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws IOException, DavException {

        if (!(resource instanceof ObservationResource)) {
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }
        ((ObservationResource) resource).unsubscribe(request.getSubscriptionId());
        response.setStatus(SC_NO_CONTENT);
    }

    /**
     * The POLL method
     *
     * @param request
     * @param response
     * @param resource
     * @throws IOException
     * @throws DavException
     */
    protected void doPoll(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    )
        throws IOException, DavException {

        if (!(resource instanceof ObservationResource)) {
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }
        EventDiscovery ed = ((ObservationResource) resource).poll(
            request.getSubscriptionId(), request.getPollTimeout());
        response.sendPollResponse(ed);
    }

    /**
     * The VERSION-CONTROL method
     *
     * @param request
     * @param response
     * @param resource
     * @throws DavException
     * @throws IOException
     */
    protected void doVersionControl(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws DavException, IOException {
        if (!(resource instanceof VersionableResource)) {
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }
        ((VersionableResource) resource).addVersionControl();
    }

    /**
     * The LABEL method
     *
     * @param request
     * @param response
     * @param resource
     * @throws DavException
     * @throws IOException
     */
    protected void doLabel(
        JWebdavRequest request,
        JWebdavResponse response,
        DavResource resource
    ) throws DavException, IOException {

        LabelInfo labelInfo = request.getLabelInfo();
        if (resource instanceof VersionResource) {
            ((VersionResource) resource).label(labelInfo);
        } else if (resource instanceof VersionControlledResource) {
            ((VersionControlledResource) resource).label(labelInfo);
        } else {
            // any other resource type that does not support a LABEL request
            response.sendError(SC_METHOD_NOT_ALLOWED);
        }
    }

    /**
     * The REPORT method
     *
     * @param request
     * @param response
     * @param resource
     * @throws DavException
     * @throws IOException
     */
    protected void doReport(JWebdavRequest request, JWebdavResponse response,
                            DavResource resource)
        throws DavException, IOException {
        ReportInfo info = request.getReportInfo();
        Report report;
        if (resource instanceof DeltaVResource) {
            report = ((DeltaVResource) resource).getReport(info);
        } else if (resource instanceof AclResource) {
            report = ((AclResource) resource).getReport(info);
        } else {
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }

        int statusCode = (report.isMultiStatusReport()) ? JDavServletResponse.SC_MULTI_STATUS : SC_OK;
        addHintAboutPotentialRequestEncodings(request, response);
        response.sendXmlResponse(report, statusCode, acceptsGzipEncoding(request) ? Collections.singletonList("gzip") : Collections.emptyList());
    }

    /**
     * The CHECKIN method
     *
     * @param request
     * @param response
     * @param resource
     * @throws DavException
     * @throws IOException
     */
    protected void doCheckin(JWebdavRequest request, JWebdavResponse response,
                             DavResource resource)
        throws DavException, IOException {

        if (!(resource instanceof VersionControlledResource)) {
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }
        String versionHref = ((VersionControlledResource) resource).checkin();
        response.setHeader(DeltaVConstants.HEADER_LOCATION, versionHref);
        response.setStatus(SC_CREATED);
    }

    /**
     * The CHECKOUT method
     *
     * @param request
     * @param response
     * @param resource
     * @throws DavException
     * @throws IOException
     */
    protected void doCheckout(JWebdavRequest request, JWebdavResponse response,
                              DavResource resource)
        throws DavException, IOException {
        if (!(resource instanceof VersionControlledResource)) {
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }
        ((VersionControlledResource) resource).checkout();
    }

    /**
     * The UNCHECKOUT method
     *
     * @param request
     * @param response
     * @param resource
     * @throws DavException
     * @throws IOException
     */
    protected void doUncheckout(JWebdavRequest request, JWebdavResponse response,
                                DavResource resource)
        throws DavException, IOException {
        if (!(resource instanceof VersionControlledResource)) {
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }
        ((VersionControlledResource) resource).uncheckout();
    }

    /**
     * The MERGE method
     *
     * @param request
     * @param response
     * @param resource
     * @throws DavException
     * @throws IOException
     */
    protected void doMerge(JWebdavRequest request, JWebdavResponse response,
                           DavResource resource) throws DavException, IOException {

        if (!(resource instanceof VersionControlledResource)) {
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }
        MergeInfo info = request.getMergeInfo();
        MultiStatus ms = ((VersionControlledResource) resource).merge(info);
        response.sendMultiStatus(ms);
    }

    /**
     * The UPDATE method
     *
     * @param request
     * @param response
     * @param resource
     * @throws DavException
     * @throws IOException
     */
    protected void doUpdate(JWebdavRequest request, JWebdavResponse response,
                            DavResource resource) throws DavException, IOException {

        if (!(resource instanceof VersionControlledResource)) {
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }
        UpdateInfo info = request.getUpdateInfo();
        MultiStatus ms = ((VersionControlledResource) resource).update(info);
        response.sendMultiStatus(ms);
    }

    /**
     * The MKWORKSPACE method
     *
     * @param request
     * @param response
     * @param resource
     * @throws DavException
     * @throws IOException
     */
    protected void doMkWorkspace(JWebdavRequest request, JWebdavResponse response,
                                 DavResource resource) throws DavException, IOException {
        if (resource.exists()) {
            log.warn("Cannot create a new workspace. Resource already exists.");
            response.sendError(SC_FORBIDDEN);
            return;
        }

        DavResource parentResource = resource.getCollection();
        if (parentResource == null || !parentResource.exists() || !parentResource.isCollection()) {
            // parent does not exist or is not a collection
            response.sendError(SC_CONFLICT);
            return;
        }
        if (!(parentResource instanceof DeltaVResource)) {
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }
        ((DeltaVResource) parentResource).addWorkspace(resource);
        response.setStatus(SC_CREATED);
    }

    /**
     * The MKACTIVITY method
     *
     * @param request
     * @param response
     * @param resource
     * @throws DavException
     * @throws IOException
     */
    protected void doMkActivity(JWebdavRequest request, JWebdavResponse response,
                                DavResource resource) throws DavException, IOException {
        if (resource.exists()) {
            log.warn("Unable to create activity: A resource already exists at the request-URL " + request.getRequestURL());
            response.sendError(SC_FORBIDDEN);
            return;
        }

        DavResource parentResource = resource.getCollection();
        if (parentResource == null || !parentResource.exists() || !parentResource.isCollection()) {
            // parent does not exist or is not a collection
            response.sendError(SC_CONFLICT);
            return;
        }
        // TODO: improve. see http://issues.apache.org/jira/browse/JCR-394
        if (!parentResource.getComplianceClass().contains(DavCompliance.ACTIVITY)) {
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }

        if (!(resource instanceof ActivityResource)) {
            log.error("Unable to create activity: ActivityResource expected");
            response.sendError(SC_INTERNAL_SERVER_ERROR);
            return;
        }

        // try to add the new activity resource
        parentResource.addMember(resource, getInputContext(request, request.getInputStream()));

        // Note: mandatory cache control header has already been set upon response creation.
        response.setStatus(SC_CREATED);
    }

    /**
     * The BASELINECONTROL method
     *
     * @param request
     * @param response
     * @param resource
     * @throws DavException
     * @throws IOException
     */
    protected void doBaselineControl(JWebdavRequest request, JWebdavResponse response,
                                     DavResource resource)
        throws DavException, IOException {

        if (!resource.exists()) {
            log.warn("Unable to add baseline control. Resource does not exist " + resource.getHref());
            response.sendError(SC_NOT_FOUND);
            return;
        }
        // TODO: improve. see http://issues.apache.org/jira/browse/JCR-394
        if (!(resource instanceof VersionControlledResource) || !resource.isCollection()) {
            log.warn("BaselineControl is not supported by resource " + resource.getHref());
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }

        // TODO : missing method on VersionControlledResource
        throw new DavException(SC_NOT_IMPLEMENTED);
        /*
        ((VersionControlledResource) resource).addBaselineControl(request.getRequestDocument());
        // Note: mandatory cache control header has already been set upon response creation.
        response.setStatus(SC_OK);
        */
    }

    /**
     * The SEARCH method
     *
     * @param request
     * @param response
     * @param resource
     * @throws DavException
     * @throws IOException
     */
    protected void doSearch(JWebdavRequest request, JWebdavResponse response,
                            DavResource resource) throws DavException, IOException {

        if (!(resource instanceof SearchResource)) {
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }
        Document doc = request.getRequestDocument();
        if (doc != null) {
            SearchInfo sR = SearchInfo.createFromXml(doc.getDocumentElement());
            response.sendMultiStatus(((SearchResource) resource).search(sR));
        } else {
            // request without request body is valid if requested resource
            // is a 'query' resource.
            response.sendMultiStatus(((SearchResource) resource).search(null));
        }
    }

    /**
     * The ACL method
     *
     * @param request
     * @param response
     * @param resource
     * @throws DavException
     * @throws IOException
     */
    protected void doAcl(JWebdavRequest request, JWebdavResponse response,
                         DavResource resource) throws DavException, IOException {
        if (!(resource instanceof AclResource)) {
            response.sendError(SC_METHOD_NOT_ALLOWED);
            return;
        }
        Document doc = request.getRequestDocument();
        if (doc == null) {
            throw new DavException(SC_BAD_REQUEST, "ACL request requires a DAV:acl body.");
        }
        AclProperty acl = AclProperty.createFromXml(doc.getDocumentElement());
        ((AclResource) resource).alterAcl(acl);
    }

    /**
     * Return a new <code>InputContext</code> used for adding resource members
     *
     * @param request
     * @param in
     * @return
     * @see #spoolResource(JWebdavRequest, JWebdavResponse, DavResource, boolean)
     */
    protected InputContext getInputContext(JDavServletRequest request, InputStream in) {
        return new InputContextImpl(request, in);
    }

    /**
     * Return a new <code>OutputContext</code> used for spooling resource properties and
     * the resource content
     *
     * @param response
     * @param out
     * @return
     * @see #doPut(JWebdavRequest, JWebdavResponse, DavResource)
     * @see #doMkCol(JWebdavRequest, JWebdavResponse, DavResource)
     */
    protected OutputContext getOutputContext(JDavServletResponse response, OutputStream out) {
        return new OutputContextImpl(response, out);
    }

    /**
     * Obtain the (ordered!) list of content codings that have been used in the
     * request
     */
    public static List<String> getContentCodings(HttpServletRequest request) {
        return getListElementsFromHeaderField(request, "Content-Encoding");
    }

    /**
     * Check whether recipient accepts GZIP content coding
     */
    private static boolean acceptsGzipEncoding(HttpServletRequest request) {
        List<String> result = getListElementsFromHeaderField(request, "Accept-Encoding");
        for (String s : result) {
            s = s.replace(" ", "");
            int semi = s.indexOf(';');
            if ("gzip".equals(s)) {
                return true;
            } else if (semi > 0) {
                String enc = s.substring(0, semi);
                String parm = s.substring(semi + 1);
                if ("gzip".equals(enc) && parm.startsWith("q=")) {
                    float q = Float.valueOf(parm.substring(2));
                    return q > 0;
                }
            }
        }
        return false;
    }

    private static List<String> getListElementsFromHeaderField(HttpServletRequest request, String fieldName) {
        List<String> result = Collections.emptyList();
        for (Enumeration<String> ceh = request.getHeaders(fieldName); ceh.hasMoreElements(); ) {
            for (String h : ceh.nextElement().split(",")) {
                if (!h.trim().isEmpty()) {
                    if (result.isEmpty()) {
                        result = new ArrayList<String>();
                    }
                    result.add(h.trim().toLowerCase(Locale.ENGLISH));
                }
            }
        }

        return result;
    }

    /**
     * Get field value of a singleton field
     *
     * @param request   HTTP request
     * @param fieldName field name
     * @return the field value (when there is indeed a single field line) or {@code null} when field not present
     * @throws IllegalArgumentException when multiple field lines present
     */
    protected static String getSingletonField(HttpServletRequest request, String fieldName) {
        Enumeration<String> lines = request.getHeaders(fieldName);
        if (!lines.hasMoreElements()) {
            return null;
        } else {
            String value = lines.nextElement();
            if (!lines.hasMoreElements()) {
                return value;
            } else {
                List<String> v = new ArrayList<>();
                v.add(value);
                while (lines.hasMoreElements()) {
                    v.add(lines.nextElement());
                }
                throw new IllegalArgumentException("Multiple field lines for '" + fieldName + "' header field: " + v);
            }
        }
    }
}
