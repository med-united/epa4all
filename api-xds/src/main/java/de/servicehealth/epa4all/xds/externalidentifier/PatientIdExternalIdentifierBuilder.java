package de.servicehealth.epa4all.xds.externalidentifier;

import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExternalIdentifierType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.InternationalStringType;

import static de.servicehealth.epa4all.xds.XDSUtils.createLocalizedString;

public abstract class PatientIdExternalIdentifierBuilder<T extends PatientIdExternalIdentifierBuilder<T>> extends ExternalIdentifierBuilder<T> {

    private final String patientId;

    public PatientIdExternalIdentifierBuilder(String patientId) {
        this.patientId = patientId;
    }

    @Override
    public ExternalIdentifierType build() {
        ExternalIdentifierType externalIdentifierPatientId = super.build();
        externalIdentifierPatientId.setId(patientId);

        return externalIdentifierPatientId;
    }
}
