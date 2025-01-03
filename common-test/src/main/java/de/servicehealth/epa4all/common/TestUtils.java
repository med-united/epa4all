package de.servicehealth.epa4all.common;

import org.apache.commons.lang3.time.StopWatch;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TestUtils {

    private static final Logger log = Logger.getLogger(TestUtils.class.getName());

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static boolean isDockerContainerRunning(String containerName) {
        ProcessBuilder processBuilder = new ProcessBuilder(
            "docker", "ps", "--filter", "name=" + containerName, "--format", "{{.Names}}"
        );
        processBuilder.redirectErrorStream(true);
        boolean running = false;
        try {
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String output = reader.lines().collect(Collectors.joining("\n"));
                running = output.contains(containerName);
            }
        } catch (Throwable t) {
            log.log(Level.SEVERE, "Error while staring 'docker ps' for " + containerName, t);
        }
        if (!running) {
            log.warning(containerName + " is not running");
        }
        return running;
    }

    public static boolean isBackendRunning(String backend) {
        boolean reachable = false;
        ProcessBuilder processBuilder = new ProcessBuilder("openssl", "s_client", "-showcerts", "-connect", backend);
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();

            Future<Boolean> future = executor.submit(() -> timed(backend, "openssl", () -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        int k = 0;
                        String line = reader.readLine();
                        while (line != null && !line.contains("CONNECTED") && k++ < 10) {
                            line = reader.readLine();
                        }
                        return line != null && line.contains("CONNECTED");
                    } catch (Throwable t) {
                        log.log(Level.SEVERE, "Error while staring 'openssl' for " + backend, t);
                        return false;
                    }
                })
            );
            try {
                reachable = future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                future.cancel(true);
            } finally {
                Thread.startVirtualThread(() -> timed(backend, "destroyForcibly", process::destroyForcibly));
            }

        } catch (Throwable t) {
            log.log(Level.SEVERE, "Error while staring 'openssl' for " + backend, t);
        }
        if (!reachable) {
            log.warning(backend + " is not reachable");
        }
        return reachable;
    }

    private static <T> T timed(String backend, String actionName, TimedAction<T> action) {
        StopWatch watch = StopWatch.createStarted();
        try {
            return action.execute();
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Error while staring '%s' for %s", actionName, backend), e);
            return null;
        } finally {
            watch.stop();
            log.info(String.format("%s %s took %s", backend, actionName, watch.formatTime()));
        }
    }

    public static void runWithDockerContainers(Set<String> containers, ITAction action) throws Exception {
        if (containers.stream().allMatch(TestUtils::isDockerContainerRunning)) {
            containers.forEach(b -> log.info(String.format("[%s] Running", b)));
            action.execute();
        } else {
            log.warning("Skipping test");
        }
    }

    public static void runWithEpaBackends(Set<String> backends, ITAction action) throws Exception {
        if (backends.stream().allMatch(TestUtils::isBackendRunning)) {
            backends.forEach(b -> log.info(String.format("[%s] Connected", b)));
            action.execute();
        } else {
            log.warning("Skipping test");
        }
    }

    public static Path getResourcePath(String... paths) {
        return Path.of("src/test/resources", paths);
    }
}
