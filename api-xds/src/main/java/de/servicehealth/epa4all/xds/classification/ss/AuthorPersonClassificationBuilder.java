package de.servicehealth.epa4all.xds.classification.ss;

import de.servicehealth.epa4all.xds.author.AuthorPerson;
import jakarta.enterprise.context.Dependent;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;

import static de.servicehealth.epa4all.xds.XDSUtils.createSlotType;

@Dependent
public class AuthorPersonClassificationBuilder extends AbstractSSClassificationBuilder<AuthorPersonClassificationBuilder> {

    public static final String SS_AUTHOR_CLASSIFICATION_SCHEME = "urn:uuid:a7058bb9-b4e4-4307-ba5b-e3f0ab85e12d";
    public static final String AUTHOR_ID = "author";

    private AuthorPerson authorPerson;
    private String telematikId;

    public AuthorPersonClassificationBuilder withAuthorPerson(AuthorPerson authorPerson) {
        this.authorPerson = authorPerson;
        return this;
    }

    public AuthorPersonClassificationBuilder withTelematikId(String telematikId) {
        this.telematikId = telematikId;
        return this;
    }

    public ClassificationType build() {
        ClassificationType classificationTypeAutor = super.build();
        classificationTypeAutor.setId(AUTHOR_ID);
        classificationTypeAutor.setClassificationScheme(SS_AUTHOR_CLASSIFICATION_SCHEME);
        classificationTypeAutor.getSlot().add(createSlotType("authorPerson", authorPerson.toString()));
        classificationTypeAutor.getSlot().add(createSlotType("authorInstitution", "Unknown^^^^^&1.2.276.0.76.4.188&ISO^^^^" + telematikId));
        classificationTypeAutor.getSlot().add(createSlotType("authorRole", "8^^^&1.3.6.1.4.1.19376.3.276.1.5.13&ISO"));
        return classificationTypeAutor;
    }

    @Override
    public String getName() {
        return "undefined";
    }
}
