package de.servicehealth.utils;

@FunctionalInterface
public interface Action<T> {

    T execute();
}
