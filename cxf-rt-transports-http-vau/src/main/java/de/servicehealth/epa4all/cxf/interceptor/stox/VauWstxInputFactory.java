package de.servicehealth.epa4all.cxf.interceptor.stox;

import com.ctc.wstx.api.ReaderConfig;
import com.ctc.wstx.cfg.XmlConsts;
import com.ctc.wstx.exc.WstxIOException;
import com.ctc.wstx.io.BranchingReaderSource;
import com.ctc.wstx.io.DefaultInputResolver;
import com.ctc.wstx.io.InputBootstrapper;
import com.ctc.wstx.io.InputSourceFactory;
import com.ctc.wstx.io.ReaderBootstrapper;
import com.ctc.wstx.io.StreamBootstrapper;
import com.ctc.wstx.io.SystemId;
import com.ctc.wstx.sr.ValidatingStreamReader;
import com.ctc.wstx.stax.WstxInputFactory;
import org.codehaus.stax2.XMLStreamReader2;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

public class VauWstxInputFactory extends WstxInputFactory {

    @Override
    public XMLStreamReader createXMLStreamReader(InputStream in, String enc)
        throws XMLStreamException
    {
        // false for auto-close, since caller has access to the input stream
        // return createSR(null, in, enc, false, false);

        SystemId systemId = null;

        boolean forER = false;
        boolean autoCloseInput = false;

        // sanity check:
        if (in == null) {
            throw new IllegalArgumentException("Null InputStream is not a valid argument");
        }
        ReaderConfig cfg = createPrivateConfig();
        if (enc == null || enc.length() == 0) {
            return createSR(cfg, systemId, StreamBootstrapper.getInstance
                (null, systemId, in), forER, autoCloseInput);
        }

        /* !!! 17-Feb-2006, TSa: We don't yet know if it's xml 1.0 or 1.1;
         *   so have to specify 1.0 (which is less restrictive WRT input
         *   streams). Would be better to let bootstrapper deal with it
         *   though:
         */
        Reader r = DefaultInputResolver.constructOptimizedReader(cfg, in, false, enc);
        return doCreateSR(cfg, systemId, ReaderBootstrapper.getInstance
            (null, systemId, r, enc), forER, autoCloseInput);
    }

    private XMLStreamReader2 doCreateSR(ReaderConfig cfg, SystemId systemId,
                                        InputBootstrapper bs, boolean forER, boolean autoCloseInput)
        throws XMLStreamException
    {
        /* Automatic closing of input: will happen always for some input
         * types (ones application has no direct access to; but can also
         * be explicitly enabled.
         */
        if (!autoCloseInput) {
            autoCloseInput = cfg.willAutoCloseInput();
        }

        Reader r;
        try {
            r = bs.bootstrapInput(cfg, true, XmlConsts.XML_V_UNKNOWN);
            if (bs.declaredXml11()) {
                cfg.enableXml11(true);
            }
        } catch (IOException ie) {
            throw new WstxIOException(ie);
        }

        /* null -> no public id available
         * false -> don't close the reader when scope is closed.
         */
        BranchingReaderSource input = InputSourceFactory.constructDocumentSource
            (cfg, bs, null, systemId, r, autoCloseInput);

        return ValidatingStreamReader.createValidatingStreamReader(input, this, cfg, bs, forER);
    }
}
