package de.servicehealth.epa4all.server.jcr.prop.custom;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.jugs.webdav.jaxrs.ConstantsAdapter;
import org.jugs.webdav.jaxrs.xml.elements.Rfc3339DateTimeFormat;
import org.jugs.webdav.util.Utilities;

import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;

import static java.util.Collections.singleton;
import static org.jugs.webdav.util.Utilities.notNull;

@XmlJavaTypeAdapter(ValidTo.Adapter.class)
@XmlRootElement(name = "validto")
public class ValidTo {

    public static final ValidTo VALIDTO = new ValidTo();

    private Date validTo;

    private ValidTo() {
    }

    public ValidTo(final Date validTo) {
        this.validTo = notNull(validTo, "validTo");
    }

    public Date getValidTo() {
        return this.validTo == null ? null : (Date) this.validTo.clone();
    }

    @XmlValue
    private final String getXmlValue() {
        return this.validTo == null ? null : new Rfc3339DateTimeFormat().format(validTo);
    }

    @SuppressWarnings("unused")
    private final void setXmlValue(final String xmlValue) throws ParseException {
        this.validTo = xmlValue == null || xmlValue.isEmpty() ? null : new Rfc3339DateTimeFormat().parse(xmlValue);
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof ValidTo that)) {
            return false;
        }
        return Objects.equals(this.validTo, that.validTo);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.validTo);
    }

    protected static final class Adapter extends ConstantsAdapter<ValidTo> {
        @Override
        protected Collection<ValidTo> getConstants() {
            return singleton(VALIDTO);
        }
    }

    @Override
    public String toString() {
        return Utilities.toString(this, this.validTo);
    }
}
