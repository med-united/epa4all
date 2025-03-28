package de.servicehealth.epa4all.xds;

import oasis.names.tc.ebxml_regrep.xsd.rim._3.LocalizedStringType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotType1;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ValueListType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

public class XDSUtils {

    public static final DateTimeFormatter LOCALDATE_YYYYMMDDHHMMSS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final Set<String> xmlCompliantMimeTypes = Set.of(
        "application/fhir+xml",
        "application/xml+fhir",
        "application/xml",
        "text/xml",
        "application/xop+xml",
        "application/hl7-v2+xml",
        "application/hl7-v3+xml",
        "application/x-cda+xml",
        "application/xds+xml",
        "application/hl7-v3",
        "application/hl7-cda+xml",
        "application/ccda+xml"
    );

    public static String generateUrnUuid() {
        return String.format("urn:uuid:%s", UUID.randomUUID());
    }

    public static boolean isXmlCompliant(String type) {
        return xmlCompliantMimeTypes.stream().anyMatch(t -> t.equalsIgnoreCase(type.trim())) || type.contains("xml");
    }

    public static boolean isPdfCompliant(String type) {
        return type.contains("pdf");
    }

    public static String generateOID() {
        // Beginne mit einem statischen Präfix, das den OID-Standard entspricht.
        // https://www.itu.int/itu-t/recommendations/rec.aspx?rec=X.667

        StringBuilder oid = new StringBuilder("2.25");
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

    public static String getNumericISO8601Timestamp(LocalDateTime dateTime) {
        return dateTime.format(LOCALDATE_YYYYMMDDHHMMSS);
    }
}
