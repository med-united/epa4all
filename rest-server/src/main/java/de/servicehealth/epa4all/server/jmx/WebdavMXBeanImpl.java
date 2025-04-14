package de.servicehealth.epa4all.server.jmx;

import de.servicehealth.api.epa4all.jmx.EpaMXBeanManager;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.concurrent.atomic.LongAdder;

@ApplicationScoped
public class WebdavMXBeanImpl implements WebdavMXBean {

    private final LongAdder counter = new LongAdder();

    void onStart(@Observes StartupEvent ev) {
        EpaMXBeanManager.registerMXBean(this, null);
    }

    @Override
    public long getRequestsCount() {
        return counter.sum();
    }

    public void countRequest() {
        counter.increment();
    }

    public void reset() {
        counter.reset();
    }
}
