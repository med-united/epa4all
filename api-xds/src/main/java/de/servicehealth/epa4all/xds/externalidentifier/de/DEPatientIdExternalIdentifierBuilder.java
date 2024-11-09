package de.servicehealth.epa4all.xds.externalidentifier.de;

import de.servicehealth.epa4all.xds.externalidentifier.PatientIdExternalIdentifierBuilder;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExternalIdentifierType;

public class DEPatientIdExternalIdentifierBuilder extends PatientIdExternalIdentifierBuilder<DEPatientIdExternalIdentifierBuilder> {

    public static final String DE_PATIENT_ID_IDENTIFICATION_SCHEME = "urn:uuid:58a6f841-87b3-4a3e-92fd-a8ffeff98427";

    public DEPatientIdExternalIdentifierBuilder(String patientId) {
        super(patientId);
    }

    @Override
    public ExternalIdentifierType build() {
        ExternalIdentifierType externalIdentifierPatientId = super.build();
        externalIdentifierPatientId.setIdentificationScheme(DE_PATIENT_ID_IDENTIFICATION_SCHEME);
        return externalIdentifierPatientId;
    }
}