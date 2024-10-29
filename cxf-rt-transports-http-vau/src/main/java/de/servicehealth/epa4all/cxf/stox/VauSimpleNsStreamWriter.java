package de.servicehealth.epa4all.cxf.stox;

import com.ctc.wstx.api.WriterConfig;
import com.ctc.wstx.sw.SimpleNsStreamWriter;
import com.ctc.wstx.sw.XmlWriter;

import javax.xml.stream.XMLStreamException;

public class VauSimpleNsStreamWriter extends SimpleNsStreamWriter {

    public VauSimpleNsStreamWriter(XmlWriter xw, String enc, WriterConfig cfg) {
        super(xw, enc, cfg);
    }

    @Override
    public void writeEndDocument() throws XMLStreamException {
        finishDocument();
    }

    private void finishDocument() throws XMLStreamException {
        // Is tree still open?
        if (mState != STATE_EPILOG) {
            if (mCheckStructure  && mState == STATE_PROLOG) {
                reportNwfStructure("Trying to write END_DOCUMENT when document has no root (ie. trying to output empty document).");
            }
            // 20-Jul-2004, TSa: Need to close the open sub-tree, if it exists...
            // First, do we have an open start element?
            if (mStartElementOpen) {
                closeStartElement(mEmptyElement);
            }
            // Then, one by one, need to close open scopes:
            /* 17-Nov-2008, TSa: that is, if we are allowed to do it
             *   (see [WSTX-165])
             */
            if (mState != STATE_EPILOG && mConfig.automaticEndElementsEnabled()) {
                do {
                    writeEndElement();
                } while (mState != STATE_EPILOG);
            }
        }

        /* And finally, inform the underlying writer that it should flush
         * and release its buffers, and close components it uses if any.
         */
        char[] buf = mCopyBuffer;
        if (buf != null) {
            mCopyBuffer = null;
            mConfig.freeMediumCBuffer(buf);
        }
    }
}
