package de.servicehealth.logging;

@FunctionalInterface
public interface ThrowingRunnable<T extends Exception> {
    void run() throws T;
}
