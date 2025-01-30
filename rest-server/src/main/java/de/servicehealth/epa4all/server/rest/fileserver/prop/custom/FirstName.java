package de.servicehealth.epa4all.server.rest.fileserver.prop.custom;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.jugs.webdav.jaxrs.ConstantsAdapter;
import org.jugs.webdav.util.Utilities;

import java.util.Collection;

import static java.util.Collections.singleton;
import static org.jugs.webdav.util.Utilities.notNull;

@XmlJavaTypeAdapter(FirstName.Adapter.class)
@XmlRootElement(name = "firstname")
public class FirstName {

    public static final FirstName FIRSTNAME = new FirstName();

    @XmlValue
    private final String name;

    private FirstName() {
        this.name = "";
    }

    public FirstName(final String name) {
        this.name = notNull(name, "name");
    }

    public final String getName() {
        return this.name;
    }

    @Override
    public final boolean equals(final Object other) {
        if (!(other instanceof FirstName that))
            return false;

        return this.name.equals(that.name);
    }

    @Override
    public final int hashCode() {
        return this.name.hashCode();
    }

    protected static final class Adapter extends ConstantsAdapter<FirstName> {
        @Override
        protected Collection<FirstName> getConstants() {
            return singleton(FIRSTNAME);
        }
    }

    @Override
    public final String toString() {
        return Utilities.toString(this, this.name);
    }
}
