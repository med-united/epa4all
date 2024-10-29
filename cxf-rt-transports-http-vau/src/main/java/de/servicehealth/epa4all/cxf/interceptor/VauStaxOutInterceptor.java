package de.servicehealth.epa4all.cxf.interceptor;

import de.servicehealth.epa4all.cxf.stox.VauWstxOutputFactory;
import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.StaxOutInterceptor;
import org.apache.cxf.io.AbstractWrappedOutputStream;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;

import javax.xml.stream.XMLStreamWriter;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;

public class VauStaxOutInterceptor extends StaxOutInterceptor {

    private static final ResourceBundle BUNDLE = BundleUtils.getBundle(StaxOutInterceptor.class);

    private final VauWstxOutputFactory factory = new VauWstxOutputFactory();

    @Override
    public void handleMessage(Message message) {
        OutputStream os = message.getContent(OutputStream.class);
        String encoding = getEncoding(message);
        try {
            os = setupOutputStream(os);
            XMLStreamWriter xwriter = factory.createXMLStreamWriter(os, encoding);
            message.setContent(XMLStreamWriter.class, xwriter);

            if (MessageUtils.getContextualBoolean(message, FORCE_START_DOCUMENT, false)) {
                xwriter.writeStartDocument(encoding, "1.0");
                message.removeContent(OutputStream.class);
                message.put(OUTPUT_STREAM_HOLDER, os);
                message.removeContent(Writer.class);
                message.put(WRITER_HOLDER, null);
            }
        } catch (Exception e) {
            throw new Fault(new org.apache.cxf.common.i18n.Message("STREAM_CREATE_EXC", BUNDLE), e);
        }

        // Add a final interceptor to write end elements
        message.getInterceptorChain().add(ENDING);
    }

    private String getEncoding(Message message) {
        Exchange ex = message.getExchange();
        String encoding = (String)message.get(Message.ENCODING);
        if (encoding == null && ex.getInMessage() != null) {
            encoding = (String) ex.getInMessage().get(Message.ENCODING);
            message.put(Message.ENCODING, encoding);
        }

        if (encoding == null) {
            encoding = StandardCharsets.UTF_8.name();
            message.put(Message.ENCODING, encoding);
        }
        return encoding;
    }

    private OutputStream setupOutputStream(OutputStream os) {
        if (!(os instanceof AbstractWrappedOutputStream)) {
            os = new AbstractWrappedOutputStream(os) { };
        }
        ((AbstractWrappedOutputStream)os).allowFlush(false);
        return os;
    }
}
