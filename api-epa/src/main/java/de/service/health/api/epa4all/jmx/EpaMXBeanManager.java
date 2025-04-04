package de.service.health.api.epa4all.jmx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.stream.Stream;

public class EpaMXBeanManager {

    private static final Logger log = LoggerFactory.getLogger(EpaMXBeanManager.class.getName());

    public static void registerMXBean(Object mbean, String qualifier) {
        var interfaces = mbean.getClass().getInterfaces();
        var mXBeanInterface = Stream.of(interfaces)
            .filter(interfaceClass ->
                interfaceClass.getPackageName().startsWith("de.service.health") &&
                    interfaceClass.getSimpleName().endsWith("MXBean")
            ).findAny().orElseThrow();
        var objectName = "de.service.health:type=" + mXBeanInterface.getSimpleName() + "_" + qualifier;
        var server = ManagementFactory.getPlatformMBeanServer();
        register(server, objectName, mbean);
    }

    private static void register(MBeanServer server, String name, Object mxbean) {
        try {
            ObjectName objectName = new ObjectName(name);
            if (server.isRegistered(objectName)) {
                log.warn(String.format("MXBean %s is already registered", name));
            } else {
                server.registerMBean(mxbean, objectName);
            }
        } catch (Exception e) {
            log.error(String.format("JMX Bean for %s was not created", name), e);
        }
    }
}
