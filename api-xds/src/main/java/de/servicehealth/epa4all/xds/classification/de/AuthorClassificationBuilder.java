package de.servicehealth.epa4all.xds.classification.de;

import de.servicehealth.epa4all.xds.author.AuthorPerson;
import jakarta.enterprise.context.Dependent;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ClassificationType;

import static de.servicehealth.epa4all.xds.XDSUtils.createSlotType;

@Dependent
public class AuthorClassificationBuilder extends AbstractDEClassificationBuilder<AuthorClassificationBuilder> {

    public static final String AUTHOR_CLASSIFICATION_SCHEME = "urn:uuid:93606bcf-9494-43ec-9b4e-a7748d1a838d";

    private AuthorPerson authorPerson;

    public AuthorClassificationBuilder withAuthorPerson(AuthorPerson authorPerson) {
        this.authorPerson = authorPerson;
        return this;
    }

    @Override
    public ClassificationType build() {
        ClassificationType authorCodeclassificationType = super.build();

        authorCodeclassificationType.setId("author-0"); // TODO
        authorCodeclassificationType.setClassificationScheme(AUTHOR_CLASSIFICATION_SCHEME);
        authorCodeclassificationType.getSlot().add(createSlotType("authorPerson", authorPerson.toString()));

        return authorCodeclassificationType;
    }

    @Override
    public String getName() {
        return "documentEntry.author";
    }
}

