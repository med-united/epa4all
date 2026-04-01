package de.servicehealth.epa4all.server.kim;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Getter
@ApplicationScoped
public class KimLdapConfig {

    @ConfigProperty(name = "kim.ldap.port", defaultValue = "636")
    int ldapPort;
}
