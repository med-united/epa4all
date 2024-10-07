package de.servicehealth.epa4all.common;

@FunctionalInterface
public interface DockerAction {
    
    void execute() throws Exception;
}
