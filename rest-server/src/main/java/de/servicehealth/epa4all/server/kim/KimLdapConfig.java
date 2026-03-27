package de.servicehealth.epa4all.server.kim;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Getter
@ApplicationScoped
public class KimLdapConfig {

    @ConfigProperty(name = "kim.ldap.port", defaultValue = "636")
    int ldapPort;

    @ConfigProperty(name = "kim.ldap.profession.oid", defaultValue = "1.2.276.0.76.4.54")
    String professionOid;
}
