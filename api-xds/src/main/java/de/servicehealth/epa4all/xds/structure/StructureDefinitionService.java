package de.servicehealth.epa4all.xds.structure;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.servicehealth.epa4all.xds.ebrim.StructureDefinition;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

@ApplicationScoped
public class StructureDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(StructureDefinitionService.class.getName());

    @ConfigProperty(name = "ig.schema.folder.path")
    String schemasFolderPath;

    @ConfigProperty(name = "ig.schema.default")
    String defaultSchema;

    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, StructureDefinition> structureDefinitions = new HashMap<>();
    private Map.Entry<String, StructureDefinition> defaultStructureDefinitionEntry;

    public void onStart(@Observes StartupEvent ev) {
        File schemaFolder = new File(schemasFolderPath);
        if (schemaFolder.exists()) {
            File[] files = schemaFolder.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                structureDefinitions = Arrays.stream(files).map(file -> {
                    try {
                        StructureDefinition structureDefinition = mapper.readValue(
                            Files.readString(file.toPath()), StructureDefinition.class
                        );
                        return Pair.of(file.getName(), structureDefinition);
                    } catch (Exception e) {
                        log.error("Unable to load ig-schema file %s".formatted(file.getAbsolutePath()), e);
                        return null;
                    }
                }).filter(Objects::nonNull).collect(Collectors.toMap(Pair::getKey, Pair::getValue));
            }
        }
        if (structureDefinitions.isEmpty()) {
            throw new Error("Unable to load ig-schema files from %s".formatted(schemaFolder.getAbsolutePath()));
        }
        defaultStructureDefinitionEntry = structureDefinitions.entrySet().stream()
            .filter(e -> e.getKey().equals(defaultSchema))
            .findFirst().orElseThrow(() ->
                new Error("IG SCHEMA configuration error - default schema not found"));
    }

    public StructureDefinition getStructureDefinition(String taskId, String igFileName, ExtrinsicContext extrinsicContext) {
        Set<Map.Entry<String, StructureDefinition>> entries = structureDefinitions.entrySet();
        Optional<Map.Entry<String, StructureDefinition>> targetEntryOpt = entries.stream()
            .filter(e -> igFileName != null && e.getKey().contains(igFileName))
            .findFirst()
            .or(() -> entries.stream()
                .filter(e -> extrinsicContext.match(e.getValue()))
                .findFirst().or(() -> Optional.of(defaultStructureDefinitionEntry)));

        Map.Entry<String, StructureDefinition> targetEntry = targetEntryOpt.orElseThrow(() ->
            new XdsException("IG SCHEMA configuration error", INTERNAL_SERVER_ERROR));

        log.info("[%s] IG Schema '%s' was selected".formatted(taskId, targetEntry.getKey()));
        return targetEntry.getValue();
    }
}
