package de.servicehealth.epa4all.xds.externalidentifier.de;

import de.servicehealth.epa4all.xds.externalidentifier.UniqueIdExternalIdentifierBuilder;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExternalIdentifierType;

public class DEUniqueIdExternalIdentifierBuilder extends UniqueIdExternalIdentifierBuilder<DEUniqueIdExternalIdentifierBuilder> {

    public static final String DE_UNIQUE_ID_IDENTIFICATION_SCHEME = "urn:uuid:2e82c1f6-a085-4c72-9da3-8640a32e42ab";

    public DEUniqueIdExternalIdentifierBuilder(String uniqueId) {
        super(uniqueId);
    }

    @Override
    public ExternalIdentifierType build() {
        ExternalIdentifierType externalIdentifierUniqueId = super.build();
        externalIdentifierUniqueId.setIdentificationScheme(DE_UNIQUE_ID_IDENTIFICATION_SCHEME);
        return externalIdentifierUniqueId;
    }
}