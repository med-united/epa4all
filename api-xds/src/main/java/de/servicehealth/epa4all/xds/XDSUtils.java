package de.servicehealth.epa4all.xds;

import oasis.names.tc.ebxml_regrep.xsd.rim._3.LocalizedStringType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotType1;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ValueListType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class XDSUtils {

    public static String generateOID() {
        // Beginne mit einem statischen Präfix, das den OID-Standard entspricht.
        StringBuilder oid = new StringBuilder("2.25");
        // https://www.itu.int/itu-t/recommendations/rec.aspx?rec=X.667
        UUID uuid = UUID.randomUUID();
        oid.append(Long.toUnsignedString(uuid.getLeastSignificantBits()));
        oid.append(".");
        oid.append(Long.toUnsignedString(uuid.getMostSignificantBits()));
        return oid.toString();
    }

    public static SlotType1 createSlotType(String value, String string) {
        SlotType1 slotType = new SlotType1();
        slotType.setName(value);
        slotType.setValueList(new ValueListType());
        slotType.getValueList().getValue().add(string);
        return slotType;
    }

    public static LocalizedStringType createLocalizedString(String lang, String value) {
        LocalizedStringType localizedStringType = new LocalizedStringType();
        if (lang != null) {
            localizedStringType.setLang(lang);
        }
        localizedStringType.setValue(value);
        return localizedStringType;
    }

    public static String getNumericISO8601Timestamp() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        return now.format(formatter);
    }
}
