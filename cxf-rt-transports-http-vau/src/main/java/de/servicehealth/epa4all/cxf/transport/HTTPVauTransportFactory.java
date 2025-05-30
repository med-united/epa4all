package de.servicehealth.epa4all.cxf.transport;

import de.servicehealth.vau.VauConfig;
import lombok.Getter;
import org.apache.cxf.Bus;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.http.DestinationRegistryImpl;
import org.apache.cxf.transport.http.HTTPConduitFactory;
import org.apache.cxf.transport.http.HTTPTransportFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HTTPVauTransportFactory extends HTTPTransportFactory {

    public static final String TRANSPORT_IDENTIFIER = "http://cxf.apache.org/transports/httpsvau";
    public static final List<String> DEFAULT_NAMESPACES = List.of(TRANSPORT_IDENTIFIER);
    public static final Set<String> URI_PREFIXES = Set.of("https+vau://");

    @Getter
    private final Set<String> uriPrefixes = new HashSet<>(URI_PREFIXES);

    protected HTTPVauConduitFactory conduitFactory;

    public HTTPVauTransportFactory(VauConfig vauConfig) {
        super(DEFAULT_NAMESPACES, new DestinationRegistryImpl());
        conduitFactory = new HTTPVauConduitFactory(vauConfig);
    }

    @Override
    protected HTTPConduitFactory findFactory(EndpointInfo endpointInfo, Bus bus) {
        return conduitFactory;
    }
}
