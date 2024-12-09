package de.servicehealth.epa4all;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.gematik.ws.conn.vsds.vsdservice.v5.VSDStatusType;

import javax.xml.datatype.DatatypeFactory;
import java.io.ByteArrayOutputStream;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.zip.GZIPOutputStream;

public class AbstractVsdTest {

    protected ReadVSDResponse prepareReadVSDResponse() throws Exception {
        ReadVSDResponse readVSDResponse = new ReadVSDResponse();
        readVSDResponse.setAllgemeineVersicherungsdaten(new byte[0]);
        readVSDResponse.setGeschuetzteVersichertendaten(new byte[0]);
        readVSDResponse.setPersoenlicheVersichertendaten(new byte[0]);

        String xml = "<PN CDM_VERSION=\"1.0.0\" xmlns=\"http://ws.gematik.de/fa/vsdm/pnw/v1.0\"><TS>20241121115318</TS><E>2</E><PZ>WDExMDQ4NTI5MTE3MzIxODk5OTdVWDFjxzDPSFvdIrRmmmOWFP/aP5rakVUqQj8=</PZ></PN>";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream os = new GZIPOutputStream(out);
        os.write(xml.getBytes());
        os.finish();

        byte[] gzipBytes = out.toByteArray();
        readVSDResponse.setPruefungsnachweis(gzipBytes);

        VSDStatusType vsdStatus = new VSDStatusType();
        vsdStatus.setStatus("0");
        vsdStatus.setVersion("5.2.0");
        DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();
        GregorianCalendar gregorianCalendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        vsdStatus.setTimestamp(datatypeFactory.newXMLGregorianCalendar(gregorianCalendar));
        readVSDResponse.setVSDStatus(vsdStatus);
        
        return readVSDResponse;
    }
}
