package de.servicehealth.epa4all.server.jcr.webdav.request.context;

import org.apache.jackrabbit.webdav.WebdavRequestContext;

public class JWebdavRequestContextHolder {

    private static final ThreadLocal<JWebdavRequestContext> tlWebdavRequestContext = new ThreadLocal<>();

    private JWebdavRequestContextHolder() {
    }

    /**
     * Return the {@link WebdavRequestContext} with the current execution thread.
     * @return the {@link WebdavRequestContext} with the current execution thread
     */
    public static JWebdavRequestContext getContext() {
        return tlWebdavRequestContext.get();
    }

    public static void setContext(JWebdavRequestContext context) {
        tlWebdavRequestContext.set(context);
    }

    public static void clearContext() {
        tlWebdavRequestContext.remove();
    }
}