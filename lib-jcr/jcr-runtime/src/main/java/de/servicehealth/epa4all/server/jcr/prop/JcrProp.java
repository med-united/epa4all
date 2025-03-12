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
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.function.Function;

import static javax.jcr.PropertyType.DATE;
import static javax.jcr.PropertyType.LONG;
import static javax.jcr.PropertyType.STRING;

@Getter
@AllArgsConstructor
public enum JcrProp {

    entries(LONG, false, entries -> new LongValue(((Entries) entries).getCount())),
    validto(DATE, true, validto -> new DateValue(toCalendar(((ValidTo) validto).getValidTo()))),
    smcb(STRING, false, smcb -> new StringValue(((Smcb) smcb).getValue())),
    creationdate(DATE, false, creationDate -> new DateValue(toCalendar(((CreationDate) creationDate).getDateTime()))),
    getlastmodified(LONG, true, getlastmodified -> new LongValue(((GetLastModified) getlastmodified).getDateTime().getTime())),
    displayname(STRING, true, displayname -> new StringValue(((DisplayName) displayname).getName())),
    getcontenttype(STRING, true, contentType -> new StringValue(((GetContentType) contentType).getMediaType())),
    getcontentlength(LONG, false, getcontentlength -> new LongValue(((GetContentLength) getcontentlength).getContentLength())),
    resourcetype(STRING, false, resourcetype -> new StringValue("COLLECTION")), // todo verify
    entryuuid(STRING, true, entryuuid -> new StringValue(((EntryUUID) entryuuid).getUuid())),
    firstname(STRING, true, firstname -> new StringValue(((FirstName) firstname).getName())),
    lastname(STRING, true, lastname -> new StringValue(((LastName) lastname).getName())),
    birthday(DATE, true, birthday -> new DateValue(toCalendar(((BirthDay) birthday).getDateTime())));

    public static final String EPA_FLEX_FOLDER = "epa:flexFolder";

    public static final String EPA_MIXIN_NAME = "epa:custom";
    public static final String EPA_NAMESPACE_PREFIX = "epa";
    public static final String EPA_NAMESPACE_URI = "https://www.service-health.de/epa";

    public static final DateTimeFormatter LOCALDATE_YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    public static final DateTimeFormatter LOCALDATE_YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final SimpleDateFormat DATE_YYYY_MM_DD = new SimpleDateFormat("yyyy-MM-dd");


    private final int type;
    private final boolean searchable;
    private final Function<Object, Value> valueFunc;

    public static Calendar toCalendar(Date date){
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        return cal;
    }

    public String epaName() {
        return EPA_NAMESPACE_PREFIX + ":" + name();
    }
}