package de.servicehealth.epa4all.server.jcr.webdav.request;

import org.apache.jackrabbit.webdav.bind.BindServletRequest;

public interface JWebdavRequest extends JDavServletRequest, JObservationDavServletRequest, JOrderingDavServletRequest,
    JTransactionDavServletRequest, JDeltaVServletRequest, BindServletRequest {
}
