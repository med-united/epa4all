package de.servicehealth.utils;

import java.net.URI;

public class URLUtils {

    public static String getBaseUrl(String url) {
        URI uri = URI.create(url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme() + "://";
        String host = uri.getHost();
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return scheme + host + port;
    }

    private URLUtils() {
    }
}
