package de.servicehealth.epa4all.server.jcr;

import de.servicehealth.epa4all.server.jcr.webdav.RepositoryProvider;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Data;

import javax.jcr.Repository;

@Data
@ApplicationScoped
public class JcrRepositoryProvider implements RepositoryProvider {

    private Repository repository;
}