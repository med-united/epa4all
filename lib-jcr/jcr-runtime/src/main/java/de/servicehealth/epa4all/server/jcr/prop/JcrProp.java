package de.servicehealth.epa4all.server.jcr.prop;

import de.servicehealth.epa4all.server.jcr.prop.custom.BirthDay;
import de.servicehealth.epa4all.server.jcr.prop.custom.Entries;
import de.servicehealth.epa4all.server.jcr.prop.custom.EntryUUID;
import de.servicehealth.epa4all.server.jcr.prop.custom.FirstName;
import de.servicehealth.epa4all.server.jcr.prop.custom.LastName;
import de.servicehealth.epa4all.server.jcr.prop.custom.Smcb;
import de.servicehealth.epa4all.server.jcr.prop.custom.ValidTo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.jackrabbit.value.DateValue;
import org.apache.jackrabbit.value.LongValue;
import org.apache.jackrabbit.value.StringValue;
import org.jugs.webdav.jaxrs.xml.properties.CreationDate;
import org.jugs.webdav.jaxrs.xml.properties.DisplayName;
import org.jugs.webdav.jaxrs.xml.properties.GetContentLength;
import org.jugs.webdav.jaxrs.xml.properties.GetContentType;
import org.jugs.webdav.jaxrs.xml.properties.GetLastModified;

import javax.jcr.Value;
import javax.jcr.nodetype.PropertyDefinition;
import java.util.Calendar;
import java.util.Date;
import java.util.function.Function;

import static javax.jcr.PropertyType.DATE;
import static javax.jcr.PropertyType.LONG;
import static javax.jcr.PropertyType.STRING;

@Getter
@AllArgsConstructor
public enum JcrProp implements MixinProp {

    firstname(STRING, false, true, firstname -> new StringValue(((FirstName) firstname).getName())),
    lastname(STRING, false, true, lastname -> new StringValue(((LastName) lastname).getName())),
    birthday(DATE, false, false, birthday -> new DateValue(toCalendar(((BirthDay) birthday).getDateTime()))),
    displayname(STRING, true, false, displayname -> new StringValue(((DisplayName) displayname).getName())),
    validto(DATE, false, false, validto -> new DateValue(toCalendar(((ValidTo) validto).getValidTo()))),
    getlastmodified(LONG, true, false, getlastmodified -> new LongValue(((GetLastModified) getlastmodified).getDateTime().getTime())),
    smcb(STRING, false, false, smcb -> new StringValue(((Smcb) smcb).getValue())),
    entryuuid(STRING, false, false, entryuuid -> new StringValue(((EntryUUID) entryuuid).getUuid())),

    resourcetype(STRING, false, false, resourcetype -> new StringValue("COLLECTION")),
    entries(LONG, false, false, entries -> new LongValue(((Entries) entries).getCount())),
    creationdate(DATE, false, false, creationDate -> new DateValue(toCalendar(((CreationDate) creationDate).getDateTime()))),
    getcontenttype(STRING, false, false, contentType -> new StringValue(((GetContentType) contentType).getMediaType())),
    getcontentlength(LONG, false, false, getcontentlength -> new LongValue(((GetContentLength) getcontentlength).getContentLength()));


    private final int type;
    private final boolean mandatory;
    private final boolean fulltext;
    private final Function<Object, Value> valueFunc;

    public static Calendar toCalendar(Date date){
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return cal;
    }

    @Override
    public String getName() {
        return EPA_NAMESPACE_PREFIX + ":" + name();
    }

    @Override
    public boolean equalTo(PropertyDefinition definition) {
        return this.fulltext == definition.isFullTextSearchable()
            && this.mandatory == definition.isMandatory()
            && this.type == definition.getRequiredType();
    }
}