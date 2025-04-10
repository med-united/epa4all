package de.servicehealth.epa4all.server.jmx;

import de.servicehealth.api.epa4all.jmx.EpaMXBeanManager;

public interface WebdavMXBean {

    String OBJECT_NAME = "de.servicehealth:type=WebdavMXBean";

    static WebdavMXBean getInstance() {
        return EpaMXBeanManager.getMXBean(OBJECT_NAME, WebdavMXBean.class);
    }

    long getRequestsCount();
}
