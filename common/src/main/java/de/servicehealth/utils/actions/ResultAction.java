package de.servicehealth.utils.actions;

@FunctionalInterface
public interface ResultAction<T> {

    T execute();
}