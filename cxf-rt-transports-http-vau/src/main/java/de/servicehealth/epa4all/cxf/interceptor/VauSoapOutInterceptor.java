package de.servicehealth.epa4all.cxf.interceptor;

import org.apache.cxf.BusFactory;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.SoapVersion;
import org.apache.cxf.binding.soap.interceptor.SoapOutInterceptor;
import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.InterceptorChain;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.EOFException;
import java.util.ResourceBundle;

import static de.servicehealth.epa4all.cxf.interceptor.InterceptorUtils.excludeInterceptors;
import static org.apache.cxf.phase.Phase.WRITE;

public class VauSoapOutInterceptor extends SoapOutInterceptor {

    private static final ResourceBundle BUNDLE = BundleUtils.getBundle(SoapOutInterceptor.class);

    public VauSoapOutInterceptor() {
        super(BusFactory.getDefaultBus(), WRITE);
    }

    @Override
    public void handleMessage(SoapMessage message) {
        super.handleMessage(message);
        InterceptorChain interceptorChain = excludeInterceptors(message, SoapOutEndingInterceptor.class);
        interceptorChain.add(new VauSoapOutEndingInterceptor());
    }

    public class VauSoapOutEndingInterceptor extends SoapOutEndingInterceptor {

        @Override
        public void handleMessage(SoapMessage message) throws Fault {
            try {
                XMLStreamWriter xtw = message.getContent(XMLStreamWriter.class);
                if (xtw != null) {
                    // Write body end
                    xtw.writeEndElement();
                    // Write Envelope end element
                    xtw.writeEndElement();
                    xtw.writeEndDocument();

                    xtw.flush();
                }
            } catch (XMLStreamException e) {
                if (e.getCause() instanceof EOFException) {
                    //Nothing we can do about this, some clients will close the connection early if
                    //they fully parse everything they need
                } else {
                    SoapVersion soapVersion = message.getVersion();
                    throw new SoapFault(new org.apache.cxf.common.i18n.Message("XML_WRITE_EXC", BUNDLE), e,
                        soapVersion.getSender());
                }
            }
        }
    }
}
