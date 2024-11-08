package de.servicehealth.epa4all.xds.externalidentifier;

import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExternalIdentifierType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.InternationalStringType;

import static de.servicehealth.epa4all.xds.XDSUtils.createLocalizedString;
import static de.servicehealth.epa4all.xds.XDSUtils.generateOID;

public abstract class UniqueIdExternalIdentifierBuilder<T extends UniqueIdExternalIdentifierBuilder<T>> extends ExternalIdentifierBuilder<T> {

    private final String uniqueId;

    public UniqueIdExternalIdentifierBuilder(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    @Override
    public ExternalIdentifierType build() {
        ExternalIdentifierType externalIdentifierUniqueId = super.build();
        externalIdentifierUniqueId.setId(uniqueId);
        externalIdentifierUniqueId.setName(new InternationalStringType());
        externalIdentifierUniqueId.getName().getLocalizedString().add(createLocalizedString(null, "XDSSubmissionSet.uniqueId"));

        return externalIdentifierUniqueId;
    }
}
