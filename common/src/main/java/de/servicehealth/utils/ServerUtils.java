package de.servicehealth.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerUtils {

    private static final Logger log = LoggerFactory.getLogger(ServerUtils.class);

    public static void terminateExecutor(ExecutorService executorService, String executorName, int awaitMillis) {
        if (executorService != null) {
            log.info(String.format("[%s] Terminating", executorName));
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(awaitMillis, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                    if (!executorService.awaitTermination(awaitMillis, TimeUnit.MILLISECONDS)) {
                        log.info(String.format("[%s] Is not terminated", executorName));
                    }
                }
            } catch (InterruptedException ex) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public static String getBaseUrl(String url) {
        URI uri = URI.create(url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme() + "://";
        String host = uri.getHost();
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return scheme + host + port;
    }

    private ServerUtils() {
    }
}