package de.servicehealth.epa4all.server.kim;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Getter
@ApplicationScoped
public class KimSmtpConfig {

    @ConfigProperty(name = "kim.from.address")
    String fromAddress;

    @ConfigProperty(name = "kim.to.address")
    String toAddress;

    @ConfigProperty(name = "kim.subject")
    String subject;

    @ConfigProperty(name = "kim.eprescription.header.name")
    String kimEprescriptionHeaderName;

    @ConfigProperty(name = "kim.eprescription.header.value")
    String kimEprescriptionHeaderValue;

    @ConfigProperty(name = "kim.equipment.header.name")
    String kimEquipmentHeaderName;

    @ConfigProperty(name = "kim.equipment.header.value")
    String kimEquipmentHeaderValue;
}