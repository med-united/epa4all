package de.servicehealth.epa4all.server.epa;

public interface KonnektorAction<T> {

    T execute() throws Exception;
}