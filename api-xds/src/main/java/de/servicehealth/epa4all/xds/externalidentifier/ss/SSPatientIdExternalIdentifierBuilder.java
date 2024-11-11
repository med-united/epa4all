package de.servicehealth.epa4all.xds.externalidentifier.ss;

import de.servicehealth.epa4all.xds.externalidentifier.PatientIdExternalIdentifierBuilder;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExternalIdentifierType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.InternationalStringType;

import static de.servicehealth.epa4all.xds.XDSUtils.createLocalizedString;

public class SSPatientIdExternalIdentifierBuilder extends PatientIdExternalIdentifierBuilder<SSPatientIdExternalIdentifierBuilder> {

    public static final String SS_PATIENT_ID_IDENTIFICATION_SCHEME = "urn:uuid:6b5aea1a-874d-4603-a4bc-96a0a7b38446";

    public SSPatientIdExternalIdentifierBuilder(String patientId) {
        super(patientId);
    }

    @Override
    public ExternalIdentifierType build() {
        ExternalIdentifierType externalIdentifierPatientId = super.build();
        externalIdentifierPatientId.setIdentificationScheme(SS_PATIENT_ID_IDENTIFICATION_SCHEME);
        externalIdentifierPatientId.setName(new InternationalStringType());
        externalIdentifierPatientId.getName().getLocalizedString().add(createLocalizedString(null, "XDSSubmissionSet.patientId"));

        return externalIdentifierPatientId;
    }
}
