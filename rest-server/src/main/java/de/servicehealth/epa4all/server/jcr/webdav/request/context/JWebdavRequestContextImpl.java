package de.servicehealth.epa4all.server.jcr.webdav.request.context;

import de.servicehealth.epa4all.server.jcr.webdav.request.JWebdavRequest;

public class JWebdavRequestContextImpl implements JWebdavRequestContext {

    private final JWebdavRequest request;

    public JWebdavRequestContextImpl(final JWebdavRequest request) {
        this.request = request;
    }

    @Override
    public JWebdavRequest getRequest() {
        return request;
    }
}
