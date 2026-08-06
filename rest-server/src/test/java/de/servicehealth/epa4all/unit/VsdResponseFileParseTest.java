package de.servicehealth.epa4all.unit;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.servicehealth.epa4all.server.vsd.VsdResponseFile;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static de.servicehealth.epa4all.common.TestUtils.getStringFixture;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VsdResponseFileParseTest {

    @Test
    public void parseAcceptsSoapEnvelopeAndBareElement() throws Exception {
        String envelopeXml = getStringFixture("ReadVSDResponseEnvelope.xml");
        ReadVSDResponse fromEnvelope = VsdResponseFile.parse(envelopeXml);
        assertNotNull(fromEnvelope.getPruefungsnachweis());
        assertNotNull(fromEnvelope.getPersoenlicheVersichertendaten());

        StringWriter sw = new StringWriter();
        JAXBContext.newInstance(ReadVSDResponse.class).createMarshaller().marshal(fromEnvelope, sw);
        ReadVSDResponse fromBareElement = VsdResponseFile.parse(sw.toString());

        assertArrayEquals(fromEnvelope.getPruefungsnachweis(), fromBareElement.getPruefungsnachweis());
        assertArrayEquals(fromEnvelope.getPersoenlicheVersichertendaten(), fromBareElement.getPersoenlicheVersichertendaten());
    }

    @Test
    public void parseRejectsXmlWithoutReadVsdResponse() {
        assertThrows(JAXBException.class, () -> VsdResponseFile.parse("<foo/>"));
    }
}
