package de.servicehealth.epa4all.server.jcr.prop.custom;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.jugs.webdav.jaxrs.ConstantsAdapter;
import org.jugs.webdav.util.Utilities;

import java.util.Collection;

import static java.util.Collections.singleton;
import static org.jugs.webdav.util.Utilities.notNull;

@XmlJavaTypeAdapter(LastName.Adapter.class)
@XmlRootElement(name = "lastname")
public class LastName {

    public static final LastName LASTNAME = new LastName();

    @XmlValue
    private final String name;

    private LastName() {
        this.name = "";
    }

    public LastName(final String name) {
        this.name = notNull(name, "name");
    }

    public final String getName() {
        return this.name;
    }

    @Override
    public final boolean equals(final Object other) {
        if (!(other instanceof LastName that))
            return false;

        return this.name.equals(that.name);
    }

    @Override
    public final int hashCode() {
        return this.name.hashCode();
    }

    protected static final class Adapter extends ConstantsAdapter<LastName> {
        @Override
        protected Collection<LastName> getConstants() {
            return singleton(LASTNAME);
        }
    }

    @Override
    public final String toString() {
        return Utilities.toString(this, this.name);
    }
}
