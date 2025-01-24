package de.servicehealth.epa4all.server.rest.fileserver.prop.custom;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.jugs.webdav.jaxrs.ConstantsAdapter;
import org.jugs.webdav.util.Utilities;

import java.util.Collection;

import static java.util.Collections.singleton;
import static org.jugs.webdav.util.Utilities.notNull;

@XmlJavaTypeAdapter(Smcb.Adapter.class)
@XmlRootElement(name = "smcb")
public class Smcb {

    public static final Smcb SMCB = new Smcb();

    @XmlValue
    private final String value;

    private Smcb() {
        this.value = "";
    }

    public Smcb(final String value) {
        this.value = notNull(value, "value");
    }

    public final String getValue() {
        return this.value;
    }

    @Override
    public final boolean equals(final Object other) {
        if (!(other instanceof Smcb that))
            return false;

        return this.value.equals(that.value);
    }

    @Override
    public final int hashCode() {
        return this.value.hashCode();
    }

    protected static final class Adapter extends ConstantsAdapter<Smcb> {
        @Override
        protected Collection<Smcb> getConstants() {
            return singleton(SMCB);
        }
    }

    @Override
    public final String toString() {
        return Utilities.toString(this, this.value);
    }
}
