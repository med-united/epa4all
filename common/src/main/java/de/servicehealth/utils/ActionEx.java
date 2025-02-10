package de.servicehealth.utils;

@FunctionalInterface
public interface ActionEx<T> {

    T execute() throws Exception;
}