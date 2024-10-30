package de.servicehealth.epa4all.cxf.interceptor.stox;

import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauResponse;
import de.servicehealth.vau.VauResponseReader;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.HttpHeaderHelper;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.StaxInEndingInterceptor;
import org.apache.cxf.interceptor.StaxInInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.staxutils.StaxUtils;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.cxf.interceptor.InterceptorUtils.getProtocolHeaders;
import static org.apache.cxf.message.Message.RESPONSE_CODE;
import static org.apache.cxf.phase.Phase.POST_STREAM;

public class VauStaxInInterceptor extends StaxInInterceptor {

    private static final Logger log = Logger.getLogger(VauStaxInInterceptor.class.getName());

    private final VauWstxInputFactory factory = new VauWstxInputFactory();
    private final VauResponseReader vauResponseReader;

    public VauStaxInInterceptor(VauClient vauClient) {
        super(POST_STREAM);
        vauResponseReader = new VauResponseReader(vauClient);
    }

    public void handleMessage(Message message) throws Fault {
        if (isGET(message) || message.getContent(XMLStreamReader.class) != null) {
            log.fine("StaxInInterceptor skipped.");
            return;
        }
        InputStream is = message.getContent(InputStream.class);
        if (is == null) {
            return;
        }
        String contentType = (String)message.get(Message.CONTENT_TYPE);

        try {
            Integer responseCode = (Integer) message.get(RESPONSE_CODE);
            VauResponse vauResponse = vauResponseReader.read(
                responseCode, getProtocolHeaders(message), is.readAllBytes()
            );
            if (vauResponse.generalError() != null) {
                throw new IllegalStateException(vauResponse.generalError());
            }
            is = new ByteArrayInputStream(vauResponse.payload());
        } catch (Exception e) {
            throw new Fault(e);
        }


        if (contentType != null
            && contentType.contains("text/html")
            && MessageUtils.isRequestor(message)) {
            StringBuilder htmlMessage = new StringBuilder(1024);
            try {
                Reader reader = new InputStreamReader(is, (String)message.get(Message.ENCODING));
                char[] s = new char[1024];
                int i = reader.read(s);
                while (htmlMessage.length() < 64536 && i > 0) {
                    htmlMessage.append(s, 0, i);
                    i = reader.read(s);
                }
            } catch (IOException e) {
                throw new Fault(new org.apache.cxf.common.i18n.Message("INVALID_HTML_RESPONSETYPE",
                    log, "(none)"));
            }
            throw new Fault(
                new org.apache.cxf.common.i18n.Message(
                    "INVALID_HTML_RESPONSETYPE", log, htmlMessage.isEmpty() ? "(none)" : htmlMessage
                )
            );
        }
        if (contentType == null) {
            //if contentType is null, this is likely a an empty post/put/delete/similar, lets see if it's
            //detectable at all
            Map<String, List<String>> m = CastUtils.cast((Map<?, ?>)message.get(Message.PROTOCOL_HEADERS));
            if (m != null) {
                List<String> contentLen = HttpHeaderHelper.getHeader(m, HttpHeaderHelper.CONTENT_LENGTH);
                List<String> contentTE = HttpHeaderHelper.getHeader(m, HttpHeaderHelper.CONTENT_TRANSFER_ENCODING);
                List<String> transferEncoding = HttpHeaderHelper.getHeader(m, HttpHeaderHelper.TRANSFER_ENCODING);
                if ((StringUtils.isEmpty(contentLen) || "0".equals(contentLen.get(0)))
                    && StringUtils.isEmpty(contentTE)
                    && (StringUtils.isEmpty(transferEncoding)
                    || !"chunked".equalsIgnoreCase(transferEncoding.get(0)))) {
                    return;
                }
            }
        }

        String encoding = (String)message.get(Message.ENCODING);

        XMLStreamReader xreader;
        try {
            xreader = factory.createXMLStreamReader(is, encoding);
            xreader = StaxUtils.configureReader(xreader, message);
        } catch (XMLStreamException e) {
            throw new Fault(new org.apache.cxf.common.i18n.Message("STREAM_CREATE_EXC",
                log,
                encoding), e);
        }
        message.setContent(XMLStreamReader.class, xreader);
        message.getInterceptorChain().add(StaxInEndingInterceptor.INSTANCE);
    }
}
