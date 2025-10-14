package de.servicehealth.epa4all.xds.classification;

import de.servicehealth.epa4all.xds.author.AuthorPerson;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;

import java.util.UUID;

import static de.servicehealth.epa4all.xds.XDSUtils.createSlotType;

@SuppressWarnings("unchecked")
public abstract class AbstractAuthorPersonClassificationBuilder<T extends AbstractAuthorPersonClassificationBuilder<T>> extends ClassificationBuilder<T> {

    private AuthorPerson authorPerson;
    private String authorInstitution;
    private String telematikId;

    public T withAuthorPerson(AuthorPerson authorPerson) {
        this.authorPerson = authorPerson;
        return (T) this;
    }

    public T withAuthorInstitution(String authorInstitution) {
        this.authorInstitution = authorInstitution;
        return (T) this;
    }

    public T withTelematikId(String telematikId) {
        this.telematikId = telematikId;
        return (T) this;
    }

    public ClassificationType build() {
        ClassificationType classificationTypeAutor = super.build();
        classificationTypeAutor.setId(UUID.randomUUID().toString());
        classificationTypeAutor.setClassificationScheme(getClassificationScheme());
        classificationTypeAutor.getSlot().add(createSlotType("authorPerson", authorPerson.toString()));
        classificationTypeAutor.getSlot().add(createSlotType("authorInstitution", authorInstitution + "^^^^^&1.2.276.0.76.4.188&ISO^^^^" + telematikId));
        classificationTypeAutor.getSlot().add(createSlotType("authorRole", "8^^^&1.3.6.1.4.1.19376.3.276.1.5.13&ISO"));
        // authorTelecommunication
        return classificationTypeAutor;
    }

    protected abstract String getClassificationScheme();

    @Override
    public String getName() {
        return "undefined";
    }
}