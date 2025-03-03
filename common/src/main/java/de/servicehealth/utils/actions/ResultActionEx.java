package de.servicehealth.utils.actions;

@FunctionalInterface
public interface ResultActionEx<T> {

    T execute() throws Exception;
}