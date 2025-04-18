package de.servicehealth.epa4all.server.jcr.webdav.request.context;

import de.servicehealth.epa4all.server.jcr.webdav.request.JWebdavRequest;

public interface JWebdavRequestContext {

    /**
     * @return the current {@link JWebdavRequest} instance associated
     * with the current thread of execution
     */
    JWebdavRequest getRequest();
}