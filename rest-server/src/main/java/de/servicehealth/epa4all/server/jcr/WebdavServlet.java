package de.servicehealth.epa4all.server.jcr;

import de.servicehealth.epa4all.server.jcr.webdav.AbstractJCRServlet;
import de.servicehealth.epa4all.server.jcr.webdav.JDavMethods;
import de.servicehealth.epa4all.server.jcr.webdav.RepositoryProvider;
import de.servicehealth.epa4all.server.jcr.webdav.request.JWebdavRequest;
import de.servicehealth.epa4all.server.jcr.webdav.request.JWebdavRequestImpl;
import de.servicehealth.epa4all.server.jcr.webdav.request.context.JWebdavRequestContextHolder;
import de.servicehealth.epa4all.server.jcr.webdav.request.context.JWebdavRequestContextImpl;
import de.servicehealth.epa4all.server.jcr.webdav.request.util.CSRFUtil;
import de.servicehealth.epa4all.server.jcr.webdav.resource.JDavResourceFactory;
import de.servicehealth.epa4all.server.jcr.webdav.response.JWebdavResponse;
import de.servicehealth.epa4all.server.jcr.webdav.response.JWebdavResponseImpl;
import de.servicehealth.epa4all.server.jcr.webdav.server.JCRWebdavServer;
import de.servicehealth.epa4all.server.jcr.webdav.server.JDavSessionProvider;
import de.servicehealth.epa4all.server.jcr.webdav.session.JBasicCredentialsProvider;
import de.servicehealth.epa4all.server.jcr.webdav.session.JSessionProviderImpl;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jackrabbit.webdav.ContentCodingAwareRequest;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavLocatorFactory;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.jcr.DavLocatorFactoryImpl;
import org.apache.jackrabbit.webdav.jcr.JcrDavSession;
import org.apache.jackrabbit.webdav.jcr.observation.SubscriptionManagerImpl;
import org.apache.jackrabbit.webdav.jcr.transaction.TxLockManagerImpl;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import javax.jcr.Session;
import java.io.IOException;
import java.io.Serial;

import static de.servicehealth.epa4all.server.jcr.webdav.JCRParams.CTX_ATTR_RESOURCE_PATH_PREFIX;
import static de.servicehealth.epa4all.server.jcr.webdav.JCRParams.INIT_PARAM_AUTHENTICATE_HEADER;
import static de.servicehealth.epa4all.server.jcr.webdav.JCRParams.INIT_PARAM_CONCURRENCY_LEVEL;
import static de.servicehealth.epa4all.server.jcr.webdav.JCRParams.INIT_PARAM_CREATE_ABSOLUTE_URI;
import static de.servicehealth.epa4all.server.jcr.webdav.JCRParams.INIT_PARAM_CSRF_PROTECTION;
import static de.servicehealth.epa4all.server.jcr.webdav.JCRParams.INIT_PARAM_RESOURCE_PATH_PREFIX;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_PRECONDITION_FAILED;

@WebServlet(urlPatterns = "/webdav2/*")
@ApplicationScoped
public class WebdavServlet extends AbstractJCRServlet {

    @Serial
    private static final long serialVersionUID = -1743484209610424817L;

    private static final Logger log = LoggerFactory.getLogger(WebdavServlet.class);

    private CSRFUtil csrfUtil;
    private JCRWebdavServer server;
    private DavLocatorFactory locatorFactory;
    private JDavResourceFactory resourceFactory;
    private TxLockManagerImpl transactionManager;

    private final RepositoryProvider repositoryProvider;
    private final JcrConfig jcrConfig;

    @Inject
    public WebdavServlet(RepositoryProvider repositoryProvider, JcrConfig jcrConfig) {
        this.repositoryProvider = repositoryProvider;
        this.jcrConfig = jcrConfig;
    }

    @Override
    public void init() throws ServletException {
        super.init();

        log.info(INIT_PARAM_CREATE_ABSOLUTE_URI + " = " + jcrConfig.isCreateAbsoluteURI());
        log.info(INIT_PARAM_AUTHENTICATE_HEADER + " = " + jcrConfig.getAuthenticateHeader());

        csrfUtil = new CSRFUtil(jcrConfig.getCsrfProtection().orElse(""));
        log.info(INIT_PARAM_CSRF_PROTECTION + " = " + jcrConfig.getCsrfProtection().orElse(""));

        String pathPrefix = jcrConfig.getResourcePathPrefix();
        log.info(INIT_PARAM_RESOURCE_PATH_PREFIX + " = " + pathPrefix);

        getServletContext().setAttribute(CTX_ATTR_RESOURCE_PATH_PREFIX, pathPrefix);

        SubscriptionManagerImpl subscriptionManager = new SubscriptionManagerImpl();
        locatorFactory = new DavLocatorFactoryImpl(pathPrefix);
        transactionManager = new TxLockManagerImpl();
        transactionManager.addTransactionListener(subscriptionManager);
        resourceFactory = new JDavResourceFactory(transactionManager, subscriptionManager);
    }

    @Override
    public JDavResourceFactory getResourceFactory() {
        return resourceFactory;
    }

    private DavLocatorFactory getLocatorFactory() {
        if (locatorFactory == null) {
            locatorFactory = new DavLocatorFactoryImpl(jcrConfig.getResourcePathPrefix());
        }
        return locatorFactory;
    }

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        JWebdavRequest webdavRequest = new JWebdavRequestImpl(request, getLocatorFactory(), jcrConfig.isCreateAbsoluteURI());
        // DeltaV requires 'Cache-Control' header for all methods except 'VERSION-CONTROL' and 'REPORT'.
        int methodCode = DavMethods.getMethodCode(request.getMethod());
        boolean deltaVMethod = JDavMethods.isDeltaVMethod(webdavRequest);
        boolean versionControlOrReport = DavMethods.DAV_VERSION_CONTROL == methodCode || DavMethods.DAV_REPORT == methodCode;
        boolean noCache = deltaVMethod && !versionControlOrReport;
        JWebdavResponse webdavResponse = new JWebdavResponseImpl(response, noCache);

        try {
            JWebdavRequestContextHolder.setContext(new JWebdavRequestContextImpl(webdavRequest));

            // make sure there is an authenticated user
            if (!getDavSessionProvider().attachSession(webdavRequest)) {
                return;
            }

            // perform referrer host checks if CSRF protection is enabled
            if (!csrfUtil.isValidRequest(webdavRequest)) {
                webdavResponse.sendError(SC_FORBIDDEN);
                return;
            }

            // check matching if=header for lock-token relevant operations
            DavResource resource = resourceFactory.createResource(webdavRequest.getRequestLocator(), webdavRequest);
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
            Session repositorySession = JcrDavSession.getRepositorySession(request.getDavSession());
            String reqWspName = resource.getLocator().getWorkspaceName();
            String wsName = repositorySession.getWorkspace().getName();
            // compare workspace names if the requested resource isn't the
            // root-collection and the request not MKWORKSPACE.
            boolean mkWorkspace = DavMethods.DAV_MKWORKSPACE == DavMethods.getMethodCode(request.getMethod());
            if (!mkWorkspace && reqWspName != null && !reqWspName.equals(wsName)) {
                return false;
            }
        } catch (DavException e) {
            log.error("Internal error: " + e.toString());
            return false;
        }

        // make sure, the TransactionId header is valid
        String txId = request.getTransactionId();
        return txId == null || transactionManager.hasLock(txId, resource);
    }

    private JDavSessionProvider getDavSessionProvider() {
        if (server == null) {
            JBasicCredentialsProvider basicCredentialsProvider = new JBasicCredentialsProvider(jcrConfig.getMissingAuthMapping());
            JSessionProviderImpl sessionProvider = new JSessionProviderImpl(basicCredentialsProvider);
            String cl = getInitParameter(INIT_PARAM_CONCURRENCY_LEVEL);
            if (cl != null) {
                try {
                    server = new JCRWebdavServer(repositoryProvider, sessionProvider, Integer.parseInt(cl));
                } catch (NumberFormatException e) {
                    log.debug("Invalid value '" + cl + "' for init-param 'concurrency-level'. Using default instead.");
                    server = new JCRWebdavServer(repositoryProvider, sessionProvider);
                }
            } else {
                server = new JCRWebdavServer(repositoryProvider, sessionProvider);
            }
        }
        return server;
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
     * Sets the "WWW-Authenticate" header and writes the appropriate error to the given webdav response.
     */
    protected void sendUnauthorized(JWebdavResponse response, DavException error) throws IOException {
        response.setHeader("WWW-Authenticate", jcrConfig.getAuthenticateHeader());
        if (error == null || error.getErrorCode() != HttpServletResponse.SC_UNAUTHORIZED) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            response.sendError(error.getErrorCode(), error.getStatusPhrase());
        }
    }
}
