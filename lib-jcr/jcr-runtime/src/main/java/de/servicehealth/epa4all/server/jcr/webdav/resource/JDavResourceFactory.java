package de.servicehealth.epa4all.server.jcr.webdav.resource;

import de.servicehealth.epa4all.server.jcr.webdav.JDavMethods;
import de.servicehealth.epa4all.server.jcr.webdav.request.JDavServletRequest;
import de.servicehealth.epa4all.server.jcr.webdav.request.JDeltaVServletRequest;
import de.servicehealth.epa4all.server.jcr.webdav.request.JTransactionDavServletRequest;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceFactory;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.apache.jackrabbit.webdav.DavServletRequest;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.DavSession;
import org.apache.jackrabbit.webdav.jcr.DefaultItemResource;
import org.apache.jackrabbit.webdav.jcr.JEventJournalResource;
import org.apache.jackrabbit.webdav.jcr.JRootCollection;
import org.apache.jackrabbit.webdav.jcr.JVersionControlledItemCollection;
import org.apache.jackrabbit.webdav.jcr.JWorkspaceResource;
import org.apache.jackrabbit.webdav.jcr.JcrDavException;
import org.apache.jackrabbit.webdav.jcr.JcrDavSession;
import org.apache.jackrabbit.webdav.jcr.VersionControlledItemCollection;
import org.apache.jackrabbit.webdav.jcr.transaction.TxLockManagerImpl;
import org.apache.jackrabbit.webdav.jcr.version.VersionHistoryItemCollection;
import org.apache.jackrabbit.webdav.jcr.version.VersionItemCollection;
import org.apache.jackrabbit.webdav.observation.ObservationResource;
import org.apache.jackrabbit.webdav.observation.SubscriptionManager;
import org.apache.jackrabbit.webdav.transaction.TransactionResource;
import org.apache.jackrabbit.webdav.version.VersionControlledResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.AccessDeniedException;
import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.observation.EventJournal;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_IMPLEMENTED;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

@SuppressWarnings("JavadocDeclaration")
public class JDavResourceFactory implements DavResourceFactory {

    private static final Logger log = LoggerFactory.getLogger(JDavResourceFactory.class);

    private final TxLockManagerImpl txMgr;
    private final SubscriptionManager subsMgr;

    public JDavResourceFactory(TxLockManagerImpl txMgr, SubscriptionManager subsMgr) {
        this.txMgr = txMgr;
        this.subsMgr = subsMgr;
    }

    @Override
    public DavResource createResource(DavResourceLocator locator, DavServletRequest request, DavServletResponse response) throws DavException {
        return null;
    }

    public DavResource createResource(DavResourceLocator locator, JDavServletRequest request) throws DavException {
        JcrDavSession.checkImplementation(request.getDavSession());
        JcrDavSession session = (JcrDavSession) request.getDavSession();

        DavResource resource;
        String type = request.getParameter("type");

        if (locator.isRootLocation()) {
            // root
            resource = new JRootCollection(locator, session, null);
        } else if ("journal".equals(type) && locator.getResourcePath().equals(locator.getWorkspacePath())) {
            // feed/event journal resource
            try {
                EventJournal ej = session.getRepositorySession().getWorkspace().getObservationManager()
                    .getEventJournal();
                if (ej == null) {
                    throw new DavException(SC_NOT_IMPLEMENTED, "event journal not supported");
                }
                resource = new JEventJournalResource(ej, locator, session, request, this);
            } catch (AccessDeniedException ex) {
                // EventJournal only allowed for admin?
                throw new DavException(SC_UNAUTHORIZED, ex);
            } catch (RepositoryException ex) {
                throw new DavException(SC_BAD_REQUEST, ex);
            }
        } else if (locator.getResourcePath().equals(locator.getWorkspacePath())) {
            // workspace resource
            resource = new JWorkspaceResource(locator, session, this);
        } else {
            // resource corresponds to a repository item
            try {
                resource = createResourceForItem(locator, session);

                Item item = getItem(session, locator);
                boolean versioned = item.isNode() && ((Node) item).isNodeType(JcrConstants.MIX_VERSIONABLE);

                /* if the created resource is version-controlled and the request
                contains a Label header, the corresponding Version must be used
                instead.*/
                if (request instanceof JDeltaVServletRequest deltaVServletRequest) {
                    String labelHeader = deltaVServletRequest.getLabel();
                    boolean affectedByLabel = JDavMethods.isMethodAffectedByLabel(request);
                    if (labelHeader != null && versioned && affectedByLabel && isVersionControlled(resource)) {
                        Version v = ((Node) item).getVersionHistory().getVersionByLabel(labelHeader);
                        DavResourceLocator vloc = locator.getFactory().createResourceLocator(locator.getPrefix(), locator.getWorkspacePath(), v.getPath(), false);
                        resource = new VersionItemCollection(vloc, session, this, v);
                    }
                }
            } catch (PathNotFoundException e) {
                /* item does not exist yet: create the default resources
                Note: MKCOL request forces a collection-resource even if there already
                exists a repository-property with the given path. the MKCOL will
                in that particular case fail with a 405 (method not allowed).*/
                if (DavMethods.getMethodCode(request.getMethod()) == DavMethods.DAV_MKCOL) {
                    resource = new VersionControlledItemCollection(locator, session, this, null);
                } else {
                    resource = new DefaultItemResource(locator, session, this, null);
                }
            } catch (RepositoryException e) {
                log.error("Failed to build resource from item '" + locator.getRepositoryPath() + "'");
                throw new JcrDavException(e);
            }
        }

        if (resource instanceof TransactionResource transactionResource
            && request instanceof JTransactionDavServletRequest transactionRequest
        ) {
            transactionResource.init(txMgr, transactionRequest.getTransactionId());
        }
        if (resource instanceof ObservationResource) {
            ((ObservationResource) resource).init(subsMgr);
        }
        return resource;
    }

    @Override
    public DavResource createResource(DavResourceLocator locator, DavSession session) throws DavException {
        JcrDavSession.checkImplementation(session);
        JcrDavSession sessionImpl = (JcrDavSession) session;

        DavResource resource;
        if (locator.isRootLocation()) {
            resource = new JRootCollection(locator, sessionImpl, null);
        } else if (locator.getResourcePath().equals(locator.getWorkspacePath())) {
            resource = new JWorkspaceResource(locator, sessionImpl, null);
        } else {
            try {
                resource = createResourceForItem(locator, sessionImpl);
            } catch (RepositoryException e) {
                log.debug("Creating resource for non-existing repository item: " + locator.getRepositoryPath());
                // todo: is this correct?
                resource = new VersionControlledItemCollection(locator, sessionImpl, null, null);
            }
        }

        // todo: currently transactionId is set manually after creation > to be improved.
        resource.addLockManager(txMgr);
        if (resource instanceof ObservationResource) {
            ((ObservationResource) resource).init(subsMgr);
        }
        return resource;
    }

    /**
     * Tries to retrieve the repository item defined by the locator's resource
     * path and build the corresponding WebDAV resource. The following distinction
     * is made between items: Version nodes, VersionHistory nodes, root node,
     * unspecified nodes and finally property items.
     *
     * @param locator
     * @param sessionImpl
     * @return DavResource representing a repository item.
     * @throws RepositoryException if {@link javax.jcr.Session#getItem(String)} fails.
     */
    private DavResource createResourceForItem(DavResourceLocator locator, JcrDavSession sessionImpl) throws RepositoryException, DavException {
        DavResource resource;
        Item item = getItem(sessionImpl, locator);
        if (item.isNode()) {
            // create special resources for Version and VersionHistory
            if (item instanceof Version) {
                resource = new VersionItemCollection(locator, sessionImpl, this, item);
            } else if (item instanceof VersionHistory) {
                resource = new VersionHistoryItemCollection(locator, sessionImpl, this, item);
            } else {
                resource = new JVersionControlledItemCollection(locator, sessionImpl, this, item);
            }
        } else {
            resource = new DefaultItemResource(locator, sessionImpl, this, item);
        }
        return resource;
    }

    protected Item getItem(JcrDavSession sessionImpl, DavResourceLocator locator)
        throws PathNotFoundException, RepositoryException {
        return sessionImpl.getRepositorySession().getItem(locator.getRepositoryPath());
    }

    /**
     * Returns true, if the specified resource is a {@link VersionControlledResource}
     * and has a version history.
     *
     * @param resource
     * @return true if the specified resource is version-controlled.
     */
    private boolean isVersionControlled(DavResource resource) {
        boolean vc = false;
        if (resource instanceof VersionControlledResource) {
            try {
                vc = ((VersionControlledResource) resource).getVersionHistory() != null;
            } catch (DavException e) {
                log.debug("Resource '" + resource.getHref() + "' is not version-controlled.");
            }
        }
        return vc;
    }
}