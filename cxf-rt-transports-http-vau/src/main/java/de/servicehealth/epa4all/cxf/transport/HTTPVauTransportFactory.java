package de.servicehealth.epa4all.cxf.transport;

import lombok.Getter;
import org.apache.cxf.Bus;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.DestinationRegistryImpl;
import org.apache.cxf.transport.http.HTTPConduitFactory;
import org.apache.cxf.transport.http.HTTPTransportFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HTTPVauTransportFactory extends HTTPTransportFactory {

    public static final String TRANSPORT_IDENTIFIER = "http://cxf.apache.org/transports/httpsvau";

    public static final List<String> DEFAULT_NAMESPACES = Collections.singletonList(TRANSPORT_IDENTIFIER);

    private static final Set<String> URI_PREFIXES = Collections.singleton("https+vau://");

    @Getter
    private final Set<String> uriPrefixes = new HashSet<>(URI_PREFIXES);

    protected HTTPVauConduitFactory conduitFactory = new HTTPVauConduitFactory();

    public HTTPVauTransportFactory() {
        super(DEFAULT_NAMESPACES, new DestinationRegistryImpl());
    }

    @Override
    protected HTTPConduitFactory findFactory(EndpointInfo endpointInfo, Bus bus) {
        return conduitFactory;
    }
}
