package de.servicehealth.epa4all.common;

@FunctionalInterface
public interface ITAction {
    
    void execute() throws Exception;
}
