package de.servicehealth.epa4all.cxf.interceptor.stox;

import com.ctc.wstx.api.WriterConfig;
import com.ctc.wstx.api.WstxOutputProperties;
import com.ctc.wstx.io.CharsetNames;
import com.ctc.wstx.stax.WstxOutputFactory;
import com.ctc.wstx.sw.BufferingXmlWriter;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class VauWstxOutputFactory extends WstxOutputFactory {

    @Override
    public XMLStreamWriter createXMLStreamWriter(OutputStream out, String enc) throws XMLStreamException {
        if (out == null) {
            throw new IllegalArgumentException("Null OutputStream is not a valid argument");
        }
        try {
            WriterConfig cfg = mConfig.createNonShared();
            if (enc == null) {
                enc = WstxOutputProperties.DEFAULT_OUTPUT_ENCODING;
            } else {
                /* Canonical ones are interned, so we may have
                 * normalized encoding already...
                 */
                if (!enc.equals(CharsetNames.CS_UTF8)
                    && !enc.equals(CharsetNames.CS_ISO_LATIN1)
                    && !enc.equals(CharsetNames.CS_US_ASCII)) {
                    enc = CharsetNames.normalize(enc);
                }
            }
            Writer w = new OutputStreamWriter(out, enc);
            BufferingXmlWriter xw = new BufferingXmlWriter(w, cfg, enc, false, out, -1);
            return new VauSimpleNsStreamWriter(xw, enc, cfg);
        } catch (IOException ex) {
            throw new XMLStreamException(ex);
        }
    }
}
