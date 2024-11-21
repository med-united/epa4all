package de.servicehealth.registry;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class BeanRegistry {

    private final Set<Object> instances = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void register(Object instance) {
        instances.add(instance);
    }

    public void unregister(Object instance) {
        instances.remove(instance);
    }

    @SuppressWarnings("unchecked")
    public <T> Collection<T> getInstances(Class<T> clazz) {
        return instances.stream().filter(clazz::isInstance).map(i -> (T) i).toList();
    }
}
