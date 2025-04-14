package de.servicehealth.epa4all.server.jmx;

public interface WebdavMXBean {

    String OBJECT_NAME = "de.servicehealth:type=WebdavMXBean";

    long getRequestsCount();
}
