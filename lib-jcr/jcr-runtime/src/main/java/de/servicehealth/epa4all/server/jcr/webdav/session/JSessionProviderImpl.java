package de.servicehealth.epa4all.server.jcr.webdav.session;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.jackrabbit.server.SessionProvider;

import javax.jcr.Credentials;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("JavadocDeclaration")
public class JSessionProviderImpl implements JSessionProvider {

    /**
     * the credentials provider
     */
    private final JCredentialsProvider cp;

    /**
     * Map of sessions acquired from custom session providers looked up
     * from request attributes. We need to keep track of such providers
     * so we can route the {@link #releaseSession(Session)} call to the
     * correct provider.
     */
    private final Map<Session, SessionProvider> externalSessions = Collections.synchronizedMap(new HashMap<>());

    /**
     * Creates a new SessionProvider
     *
     * @param cp
     */
    public JSessionProviderImpl(JCredentialsProvider cp) {
        this.cp = cp;
    }

    /**
     * {@inheritDoc }
     */
    public Session getSession(
        HttpServletRequest request, Repository repository, String workspace
    ) throws ServletException, RepositoryException {
        Session s;

        // JCR-3222: Check if a custom session provider is available as a
        // request attribute. If one is available, ask it first for a session.
        // Object object = request.getAttribute(SessionProvider.class.getName());
        // if (object instanceof SessionProvider) {
        //     SessionProvider provider = (SessionProvider) object;
        //     s = provider.getSession(request, repository, workspace);
        //     if (s != null) {
        //         externalSessions.put(s, provider);
        //     }
        // }

        Credentials creds = cp.getCredentials(request);
        if (creds == null) {
            s = repository.login(workspace);
        } else {
            s = repository.login(creds, workspace);
        }

        return s;
    }

    /**
     * {@inheritDoc }
     */
    public void releaseSession(Session session) {
        // JCR-3222: If the session was acquired from a custom session
        // provider, we need to ask that provider to release the session.
        SessionProvider provider = externalSessions.remove(session);
        if (provider != null) {
            provider.releaseSession(session);
        } else {
            session.logout();
        }
    }
}