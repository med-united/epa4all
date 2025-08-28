package de.servicehealth.epa4all.common;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.FileUtils.deleteDirectory;

public class TestUtils {

    public final static String WIREMOCK = "wiremock" + File.separator;
    public final static String FIXTURES = WIREMOCK + "fixtures";

    private static final Logger log = LoggerFactory.getLogger(TestUtils.class.getName());

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
            log.error("Error while staring 'docker ps' for " + containerName, t);
        }
        if (!running) {
            log.warn(containerName + " is not running");
        }
        return running;
    }

    public static boolean isBackendReachable(String backend) {
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
                        log.error("Error while staring 'openssl' for " + backend, t);
                        return false;
                    }
                })
            );
            try {
                reachable = future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                future.cancel(true);
            } finally {
                Thread.startVirtualThread(process::destroyForcibly);
            }

        } catch (Throwable t) {
            log.error("Error while staring 'openssl' for " + backend, t);
        }
        if (!reachable) {
            log.warn(backend + " is not reachable");
        }
        return reachable;
    }

    private static <T> T timed(String subject, String actionName, TimedAction<T> action) throws Exception {
        StopWatch watch = StopWatch.createStarted();
        try {
            return action.execute();
        } catch (Exception e) {
            log.error(String.format("[%s] Error while running '%s' action", subject, actionName), e);
            throw e;
        } finally {
            watch.stop();
            log.info(String.format("[%s] '%s' action took %s", subject, actionName, watch.formatTime()));
        }
    }

    public static void runWithDockerContainers(Set<String> containers, ITAction action) throws Exception {
        timed("Docker containers", "run test", () -> {
            if (containers.parallelStream().allMatch(TestUtils::isDockerContainerRunning)) {
                containers.forEach(b -> log.info(String.format("[%s] Running", b)));
                action.execute();
            } else {
                log.warn("Skipping test");
            }
            return null;
        });
    }

    public static void runWithEpaBackends(Set<String> backends, ITAction action) throws Exception {
        timed("ePA backends", "run test", () -> {
            if (backends.parallelStream().allMatch(TestUtils::isBackendReachable)) {
                backends.forEach(b -> log.info(String.format("[%s] Connected", b)));
                action.execute();
            } else {
                log.warn("Skipping test");
            }
            return null;
        });
    }

    public static Path getResourcePath(String... paths) {
        return Path.of("src" + File.separator + "test" + File.separator + "resources", paths);
    }

    public static String getStringFixture(String fileName) throws Exception {
        return Files.readString(getResourcePath(FIXTURES, fileName), UTF_8);
    }

    public static byte[] getTextFixture(String fileName) throws Exception {
        return Files.readString(getResourcePath(FIXTURES, fileName)).getBytes(UTF_8);
    }

    public static byte[] getBinaryFixture(String fileName) throws Exception {
        return Files.readAllBytes(getResourcePath(FIXTURES, fileName));
    }

    public static void deleteFiles(File[] files) {
        if (files != null) {
            Stream.of(files).forEach(f -> {
                if (f.isDirectory()) {
                    try {
                        deleteDirectory(f);
                    } catch (IOException e) {
                        log.error(e.getMessage());
                    }
                } else {
                    f.delete();
                }
            });
        }
    }
}
