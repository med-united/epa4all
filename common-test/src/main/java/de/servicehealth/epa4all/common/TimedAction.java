package de.servicehealth.epa4all.common;

public interface TimedAction<T> {

    T execute() throws Exception;
}