package de.servicehealth.epa4all.xds.externalidentifier.ss;

import de.servicehealth.epa4all.xds.externalidentifier.UniqueIdExternalIdentifierBuilder;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExternalIdentifierType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.InternationalStringType;

import static de.servicehealth.epa4all.xds.XDSUtils.createLocalizedString;

public class SSUniqueIdExternalIdentifierBuilder extends UniqueIdExternalIdentifierBuilder<SSUniqueIdExternalIdentifierBuilder> {

    public static final String SS_UNIQUE_ID_IDENTIFICATION_SCHEME = "urn:uuid:96fdda7c-d067-4183-912e-bf5ee74998a8";

    public SSUniqueIdExternalIdentifierBuilder(String uniqueId) {
        super(uniqueId);
    }

    @Override
    public ExternalIdentifierType build() {
        ExternalIdentifierType externalIdentifierUniqueId = super.build();
        externalIdentifierUniqueId.setIdentificationScheme(SS_UNIQUE_ID_IDENTIFICATION_SCHEME);
        externalIdentifierUniqueId.setName(new InternationalStringType());
        externalIdentifierUniqueId.getName().getLocalizedString().add(createLocalizedString(null, "XDSSubmissionSet.uniqueId"));

        return externalIdentifierUniqueId;
    }
}
