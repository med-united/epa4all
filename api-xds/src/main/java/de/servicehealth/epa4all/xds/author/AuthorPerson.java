package de.servicehealth.epa4all.xds.author;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthorPerson {

    private String id;
    private String firstName;
    private String lastName;
    private String title;

    @Override
    public String toString() {
        return String.format("%s^%s^%s^^^%s^^^&1.2.276.0.76.4.16&ISO", id, firstName, lastName, title);
    }
}
