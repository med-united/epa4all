package de.servicehealth.epa4all.xds.externalidentifier.de;

import de.servicehealth.epa4all.xds.externalidentifier.UniqueIdExternalIdentifierBuilder;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExternalIdentifierType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.InternationalStringType;

import static de.servicehealth.epa4all.xds.XDSUtils.createLocalizedString;

public class DEUniqueIdExternalIdentifierBuilder extends UniqueIdExternalIdentifierBuilder<DEUniqueIdExternalIdentifierBuilder> {

    public static final String DE_UNIQUE_ID_IDENTIFICATION_SCHEME = "urn:uuid:2e82c1f6-a085-4c72-9da3-8640a32e42ab";

    public DEUniqueIdExternalIdentifierBuilder(String uniqueId) {
        super(uniqueId);
    }

    @Override
    public ExternalIdentifierType build() {
        ExternalIdentifierType externalIdentifierUniqueId = super.build();
        externalIdentifierUniqueId.setIdentificationScheme(DE_UNIQUE_ID_IDENTIFICATION_SCHEME);
        externalIdentifierUniqueId.setName(new InternationalStringType());
        externalIdentifierUniqueId.getName().getLocalizedString().add(createLocalizedString(null, "XDSDocumentEntry.uniqueId"));

        return externalIdentifierUniqueId;
    }
}