package de.servicehealth.epa4all.server.logging;

@FunctionalInterface
public interface ThrowingRunnable<T extends Exception> {
    void run() throws T;
}
