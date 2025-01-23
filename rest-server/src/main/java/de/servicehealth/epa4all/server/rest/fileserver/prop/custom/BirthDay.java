package de.servicehealth.epa4all.server.rest.fileserver.prop.custom;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.jugs.webdav.jaxrs.ConstantsAdapter;
import org.jugs.webdav.jaxrs.xml.elements.Rfc1123DateFormat;
import org.jugs.webdav.util.Utilities;

import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;

import static java.util.Collections.singleton;
import static org.jugs.webdav.util.Utilities.notNull;

@XmlJavaTypeAdapter(BirthDay.Adapter.class)
@XmlRootElement(name = "birthday")
public class BirthDay {

    public static final BirthDay BIRTHDAY = new BirthDay();

    private Date dateTime;

    private BirthDay() {
    }

    public BirthDay(final Date dateTime) {
        this.dateTime = notNull(dateTime, "dateTime");
    }

    public final Date getDateTime() {
        return this.dateTime == null ? null : (Date) this.dateTime.clone();
    }

    @XmlValue
    private String getXmlValue() {
        return this.dateTime == null ? null : new Rfc1123DateFormat().format(this.dateTime);
    }

    @SuppressWarnings("unused")
    private final void setXmlValue(final String xmlValue) throws ParseException {
        this.dateTime = xmlValue == null || xmlValue.isEmpty() ? null : new Rfc1123DateFormat().parse(xmlValue);
    }

    @Override
    public final boolean equals(final Object other) {
        if (!(other instanceof BirthDay that)) {
            return false;
        }
        return Objects.equals(this.dateTime, that.dateTime);
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(this.dateTime);
    }
    
    protected static final class Adapter extends ConstantsAdapter<BirthDay> {
        @Override
        protected final Collection<BirthDay> getConstants() {
            return singleton(BIRTHDAY);
        }
    }

    @Override
    public final String toString() {
        return Utilities.toString(this, this.dateTime);
    }
}
