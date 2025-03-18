package org.apache.jackrabbit.webdav.jcr;

import org.apache.jackrabbit.webdav.DavResourceFactory;
import org.apache.jackrabbit.webdav.DavResourceLocator;

public class JRootCollection extends RootCollection {

    public JRootCollection(DavResourceLocator locator, JcrDavSession session, DavResourceFactory factory) {
        super(locator, session, factory);
    }
}