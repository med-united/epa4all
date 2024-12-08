package de.servicehealth.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerUtils {

    private static final Logger log = LoggerFactory.getLogger(ServerUtils.class);

    public static String getBaseUrl(String url) {
        URI uri = URI.create(url.replace("+vau", ""));
        String scheme = uri.getScheme() == null ? "" : uri.getScheme() + "://";
        String host = uri.getHost();
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return scheme + host + port;
    }

    public static String getBackendUrl(String backend, String serviceUrl) {
        return serviceUrl.replace("[epa-backend]", backend);
    }

    private ServerUtils() {
    }
}
