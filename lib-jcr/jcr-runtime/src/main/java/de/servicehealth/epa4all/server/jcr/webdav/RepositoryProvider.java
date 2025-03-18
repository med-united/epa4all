package de.servicehealth.epa4all.server.jcr.webdav;

import javax.jcr.Repository;

public interface RepositoryProvider {

    Repository getRepository();
}
