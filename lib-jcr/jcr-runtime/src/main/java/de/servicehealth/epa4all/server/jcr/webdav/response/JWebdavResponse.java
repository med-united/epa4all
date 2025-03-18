package de.servicehealth.epa4all.server.jcr.webdav.response;

import java.util.Map;
import java.util.function.Supplier;

public interface JWebdavResponse extends JDavServletResponse, JObservationDavServletResponse {

    // can be removed when we move to Servlet API 4.0
    default void setTrailerFields(Supplier<Map<String, String>> supplier) {
        // nop
    }

    // can be removed when we move to Servlet API 4.0
    default Supplier<Map<String, String>> getTrailerFields() {
        return null;
    }
}