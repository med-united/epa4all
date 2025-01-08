package de.servicehealth.epa4all.server.epa;

import jakarta.ws.rs.core.Response;

public interface FhirResponseAction {

    Response execute() throws Exception;
}
