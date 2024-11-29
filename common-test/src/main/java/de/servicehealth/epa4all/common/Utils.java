package de.servicehealth.epa4all.common;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Utils {
	
	private static Logger log = Logger.getLogger(Utils.class.getName());

    public static boolean isDockerContainerRunning(String containerName) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
            "docker", "ps", "--filter", "name=" + containerName, "--format", "{{.Names}}"
        );
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        process.waitFor(5, TimeUnit.SECONDS);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String output = reader.lines().collect(Collectors.joining("\n"));
            return output.contains(containerName);
        }
    }

    public static void runWithDocker(String service, DockerAction action) throws Exception {
        if (isDockerContainerRunning(service)) {
            action.execute();
        } else {
        	log.warning("Docker not running not executing tests.");
        }
    }
}
