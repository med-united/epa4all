package de.servicehealth.epa4all.server.epa;

import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;

public interface XdsResponseAction<T extends RegistryResponseType> {

    T execute() throws Exception;
}
