package de.servicehealth.epa4all.server.jcr.webdav.session;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.jackrabbit.server.SessionProvider;

import javax.jcr.LoginException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

@SuppressWarnings("JavadocDeclaration")
public interface JSessionProvider {

    /**
     * Provides the repository session suitable for the given request.
     *
     * @param request
     * @param rep the repository to login
     * @param workspace the workspace name
     * @return the session or null
     * @throws LoginException if the credentials are invalid
     * @throws ServletException if an error occurs
     */
    Session getSession(HttpServletRequest request, Repository rep, String workspace) throws ServletException, RepositoryException;

    /**
     * Informs this provider that the session acquired by a previous
     * {@link SessionProvider#getSession} call is no longer needed.
     *
     * @param session
     */
    void releaseSession(Session session);
}