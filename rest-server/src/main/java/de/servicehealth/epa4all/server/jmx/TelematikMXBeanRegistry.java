package de.servicehealth.epa4all.server.jmx;

import de.servicehealth.api.epa4all.jmx.EpaMXBeanManager;
import de.servicehealth.folder.IFolderService;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@ApplicationScoped
public class TelematikMXBeanRegistry {

    private static final Logger log = LoggerFactory.getLogger(TelematikMXBeanRegistry.class.getName());

    private final Map<String, LongAdder> telematikPatientsCount = new ConcurrentHashMap<>();

    @Inject
    IFolderService folderService;

    void onStart(@Observes @Priority(30) StartupEvent ev) {
        Arrays.stream(folderService.getTelematikFolders()).map(File::getName).forEach(this::registerTelematikId);
    }

    public void registerTelematikId(String telematikId) {
        File telematikFolder = folderService.getTelematikFolder(telematikId);

        String qualifier = "_" + telematikId;
        log.info("Registering TelematikMXBean" + qualifier);
        LongAdder longAdder = new LongAdder();
        longAdder.add(folderService.getNestedFolders(telematikFolder).length);
        telematikPatientsCount.put(telematikId, longAdder);
        TelematikMXBean telematikMXBean = () -> telematikPatientsCount.get(telematikId).sum();
        EpaMXBeanManager.registerMXBean(telematikMXBean, qualifier);
    }

    public void registerNewPatient(String telematikId) {
        telematikPatientsCount.computeIfAbsent(telematikId, b -> new LongAdder()).increment();
    }

    public void unregisterPatient(String telematikId) {
        telematikPatientsCount.computeIfAbsent(telematikId, b -> new LongAdder()).decrement();
    }
}
