package de.servicehealth.epa4all.server.jcr.webdav.server;

import de.servicehealth.epa4all.server.jcr.webdav.request.JWebdavRequest;
import org.apache.jackrabbit.webdav.DavException;

@SuppressWarnings("JavadocDeclaration")
public interface JDavSessionProvider {

    /**
     * Acquires a DavSession. Upon success, the WebdavRequest will
     * reference that session.
     * <p>
     * A session will not be available if an exception is thrown.
     *
     * @param request
     * @return <code>true</code> if the session was attached to the request;
     *         <code>false</code> otherwise.
     * @throws DavException if a problem occurred while obtaining the session
     */
    boolean attachSession(JWebdavRequest request) throws DavException;

    /**
     * Releases the reference from the request to the session.
     *
     * @param request
     */
    void releaseSession(JWebdavRequest request);
}