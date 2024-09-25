package de.servicehealth.epa4all.common;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.codec.binary.Base64;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Utils {

    public static boolean isDockerServiceRunning(String containerName) throws Exception {
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
}
