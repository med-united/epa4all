package de.servicehealth.epa4all.cxf.transport;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.cxf.Bus;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.DestinationRegistry;
import org.apache.cxf.transport.http.HTTPConduitFactory;
import org.apache.cxf.transport.http.HTTPTransportFactory;

public class HTTPVAUTransportFactory extends HTTPTransportFactory {

    public static final String TRANSPORT_IDENTIFIER = "http://cxf.apache.org/transports/httpsvau";

    public static final List<String> DEFAULT_NAMESPACES = Collections.singletonList(TRANSPORT_IDENTIFIER);

    private static final Set<String> URI_PREFIXES = Collections.singleton("https+vau://");

    private Set<String> uriPrefixes = new HashSet<>(URI_PREFIXES);

    protected HTTPVAUConduitFactory conduitFactory = new HTTPVAUConduitFactory();

    public HTTPVAUTransportFactory() {
        super();
    }

    public HTTPVAUTransportFactory(DestinationRegistry registry) {
        super(DEFAULT_NAMESPACES, registry);
    }

    public Set<String> getUriPrefixes() {
        return uriPrefixes;
    }

    @Override
    protected HTTPConduitFactory findFactory(EndpointInfo endpointInfo, Bus bus) {
        return conduitFactory;
    }
}
