package de.servicehealth.epa4all.xds.extrinsic;

import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExternalIdentifierType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.InternationalStringType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.LocalizedStringType;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static de.servicehealth.epa4all.xds.XDSUtils.createSlotType;
import static de.servicehealth.epa4all.xds.XDSUtils.getNumericISO8601Timestamp;

@SuppressWarnings("unchecked")
public class ExtrinsicObjectTypeBuilder<T extends ExtrinsicObjectTypeBuilder<T>> {

    private ClassificationType[] classificationTypes;
    private ExternalIdentifierType[] externalIdentifierTypes;

    protected String documentId;
    protected String mimeType;
    protected String uniqueIdValue;
    protected String languageCode;
    protected String value;

    public T withDocumentId(String documentId) {
        this.documentId = documentId;
        return (T) this;
    }

    public T withClassifications(ClassificationType... classificationTypes) {
        this.classificationTypes = classificationTypes;
        return (T) this;
    }

    public T withExternalIdentifiers(ExternalIdentifierType... externalIdentifierTypes) {
        this.externalIdentifierTypes = externalIdentifierTypes;
        return (T) this;
    }

    public T withMimeType(String mimeType) {
        this.mimeType = mimeType;
        return (T) this;
    }

    public T withUniqueId(String uniqueIdValue) {
        this.uniqueIdValue = uniqueIdValue;
        return (T) this;
    }

    public T withLanguageCode(String languageCode) {
        this.languageCode = languageCode;
        return (T) this;
    }

    public T withValue(String value) {
        this.value = value;
        return (T) this;
    }

    public ExtrinsicObjectType build() {
        ExtrinsicObjectType extrinsicObjectType = new ExtrinsicObjectType();
        extrinsicObjectType.setId(documentId);
        extrinsicObjectType.setMimeType(mimeType);

        String numericISO8601Timestamp = getNumericISO8601Timestamp(LocalDateTime.now().minusDays(7)); // TODO get right date
        extrinsicObjectType.getSlot().add(createSlotType("creationTime", numericISO8601Timestamp));
        extrinsicObjectType.getSlot().add(createSlotType("languageCode", languageCode));
        extrinsicObjectType.getSlot().add(createSlotType("URI", uniqueIdValue));

        LocalizedStringType localizedStringType = new LocalizedStringType();
        localizedStringType.setLang(languageCode);
        localizedStringType.setValue(value);
        extrinsicObjectType.setName(new InternationalStringType());
        extrinsicObjectType.getName().getLocalizedString().add(localizedStringType);

        if (classificationTypes != null) {
            Stream.of(classificationTypes).forEach(extrinsicObjectType.getClassification()::add);
        }
        if (externalIdentifierTypes != null) {
            Stream.of(externalIdentifierTypes).forEach(extrinsicObjectType.getExternalIdentifier()::add);
        }

        return extrinsicObjectType;
    }
}
