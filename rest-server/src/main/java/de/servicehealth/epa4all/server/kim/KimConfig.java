package de.servicehealth.epa4all.server.kim;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Getter
@ApplicationScoped
public class KimConfig {

    @ConfigProperty(name = "kim.from.address")
    String fromAddress;

    @ConfigProperty(name = "kim.to.address")
    String toAddress;

    @ConfigProperty(name = "kim.subject")
    String subject;

    @ConfigProperty(name = "kim.dienstkennung.header")
    String dienstkennungHeader;
}