package org.apache.jackrabbit.webdav.jcr;

import org.apache.jackrabbit.webdav.DavResourceFactory;
import org.apache.jackrabbit.webdav.DavResourceLocator;

public class JWorkspaceResourceImpl extends WorkspaceResourceImpl {

    public JWorkspaceResourceImpl(DavResourceLocator locator, JcrDavSession session, DavResourceFactory factory) {
        super(locator, session, factory);
    }
}
