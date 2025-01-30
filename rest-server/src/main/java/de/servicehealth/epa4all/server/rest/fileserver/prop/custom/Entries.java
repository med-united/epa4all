package de.servicehealth.epa4all.server.rest.fileserver.prop.custom;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.jugs.webdav.jaxrs.ConstantsAdapter;
import org.jugs.webdav.util.Utilities;

import java.util.Collection;
import java.util.Objects;

import static java.util.Collections.singleton;

@XmlJavaTypeAdapter(Entries.Adapter.class)
@XmlRootElement(name = "entries")
public class Entries {

    public static final Entries ENTRIES = new Entries();

    private Integer count;

    @SuppressWarnings("unused")
    private String getXmlValue() {
        return this.count == null ? null : Integer.toString(this.count);
    }

    @XmlValue
    private void setXmlValue(final String xmlValue) {
        this.count = xmlValue == null || xmlValue.isEmpty() ? null : Integer.parseInt(xmlValue);
    }

    private Entries() {
    }

    public Entries(int count) {
        this.count = count;
    }

    public int getCount() {
        return this.count == null ? 0 : this.count;
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof Entries that)) {
            return false;
        }
        return Objects.equals(this.count, that.count);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.count);
    }

    protected static final class Adapter extends ConstantsAdapter<Entries> {
        @Override
        protected Collection<Entries> getConstants() {
            return singleton(ENTRIES);
        }
    }

    @Override
    public final String toString() {
        return Utilities.toString(this, this.count);
    }
}
