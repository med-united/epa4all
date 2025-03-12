package de.servicehealth.epa4all.server.jcr.prop.custom;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.jugs.webdav.jaxrs.ConstantsAdapter;
import org.jugs.webdav.util.Utilities;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;

import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.DATE_YYYY_MM_DD;
import static de.servicehealth.epa4all.server.jcr.prop.JcrProp.LOCALDATE_YYYY_MM_DD;
import static de.servicehealth.utils.ServerUtils.asDate;
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

    public Date getDateTime() {
        return this.dateTime == null ? null : (Date) this.dateTime.clone();
    }

    @XmlValue
    private String getXmlValue() {
        return this.dateTime == null ? null : DATE_YYYY_MM_DD.format(dateTime);
    }

    @SuppressWarnings("unused")
    private void setXmlValue(final String xmlValue) throws ParseException {
        this.dateTime = xmlValue == null || xmlValue.isEmpty() ? null : asDate(LocalDate.parse(xmlValue, LOCALDATE_YYYY_MM_DD));
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof BirthDay that)) {
            return false;
        }
        return Objects.equals(this.dateTime, that.dateTime);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.dateTime);
    }
    
    protected static final class Adapter extends ConstantsAdapter<BirthDay> {
        @Override
        protected Collection<BirthDay> getConstants() {
            return singleton(BIRTHDAY);
        }
    }

    @Override
    public String toString() {
        return Utilities.toString(this, this.dateTime);
    }
}
