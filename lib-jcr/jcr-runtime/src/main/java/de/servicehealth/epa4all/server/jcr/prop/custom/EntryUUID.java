package de.servicehealth.epa4all.server.jcr.prop.custom;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.jugs.webdav.jaxrs.ConstantsAdapter;
import org.jugs.webdav.util.Utilities;

import java.util.Collection;

import static java.util.Collections.singleton;
import static org.jugs.webdav.util.Utilities.notNull;

@XmlJavaTypeAdapter(EntryUUID.Adapter.class)
@XmlRootElement(name = "entryuuid")
public class EntryUUID {

    public static final EntryUUID ENTRYUUID = new EntryUUID();

    @XmlValue
    private final String uuid;

    private EntryUUID() {
        this.uuid = "";
    }

    public EntryUUID(final String uuid) {
        this.uuid = notNull(uuid, "entryUuid");
    }

    public final String getUuid() {
        return this.uuid;
    }

    @Override
    public final boolean equals(final Object other) {
        if (!(other instanceof EntryUUID that))
            return false;

        return this.uuid.equals(that.uuid);
    }

    @Override
    public final int hashCode() {
        return this.uuid.hashCode();
    }

    protected static final class Adapter extends ConstantsAdapter<EntryUUID> {
        @Override
        protected Collection<EntryUUID> getConstants() {
            return singleton(ENTRYUUID);
        }
    }

    @Override
    public final String toString() {
        return Utilities.toString(this, this.uuid);
    }
}
