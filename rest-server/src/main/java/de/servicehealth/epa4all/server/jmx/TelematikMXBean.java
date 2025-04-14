package de.servicehealth.epa4all.server.jmx;

public interface TelematikMXBean {

    String OBJECT_NAME = "de.servicehealth:type=TelematikMXBean_%s";

    long getPatientsCount();
}
