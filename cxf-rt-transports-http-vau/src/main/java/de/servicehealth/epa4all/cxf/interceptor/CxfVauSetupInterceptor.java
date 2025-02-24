package de.servicehealth.epa4all.cxf.interceptor;

import de.servicehealth.vau.VauClient;
import de.servicehealth.vau.VauFacade;
import de.servicehealth.vau.VauInfo;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.http.HttpClientHTTPConduit;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Security;

import static de.servicehealth.vau.VauClient.VAU_CID;
import static de.servicehealth.vau.VauClient.VAU_CLIENT_UUID;
import static de.servicehealth.vau.VauClient.VAU_NON_PU_TRACING;
import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static de.servicehealth.vau.VauClient.X_WORKPLACE;
import static org.apache.cxf.message.Message.PROTOCOL_HEADERS;

public class CxfVauSetupInterceptor extends AbstractPhaseInterceptor<Message> {

    private static final Logger log = LoggerFactory.getLogger(CxfVauSetupInterceptor.class.getName());

    static {
        Security.addProvider(new BouncyCastlePQCProvider());
        Security.addProvider(new BouncyCastleProvider());
    }

    private final VauFacade vauFacade;

    public CxfVauSetupInterceptor(VauFacade vauFacade) {
        super(Phase.SETUP);
        this.vauFacade = vauFacade;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleMessage(Message message) throws Fault {
        Conduit conduit = message.getExchange().getConduit(message);
        if (conduit instanceof HttpClientHTTPConduit) {
            MetadataMap<String, String> headers = (MetadataMap<String, String>) message.get(PROTOCOL_HEADERS);

            // not used for SOAP
            String vauClientUuid = headers == null ? null : headers.getFirst(VAU_CLIENT_UUID);
            String konnektor = headers == null ? (String) message.get(X_KONNEKTOR) : headers.getFirst(X_KONNEKTOR);
            String workplace = headers == null ? (String) message.get(X_WORKPLACE) : headers.getFirst(X_WORKPLACE);
            VauClient vauClient;
            try {
                vauClient = vauClientUuid == null
                    ? vauFacade.acquire(konnektor, workplace)
                    : vauFacade.acquire(vauClientUuid);

                VauInfo vauInfo = vauClient.getVauInfo();
                if (vauInfo == null) {
                    throw new IllegalStateException("Empty VAU client is acquired, uuid = " + vauClientUuid);
                }
                message.put(VAU_CID, vauInfo.getVauCid());
                message.put(VAU_NON_PU_TRACING, vauInfo.getVauNonPUTracing());
                
            } catch (Exception e) {
                log.error("Error while acquiring VAU client", e);
                throw new Fault(e);
            }
        }
    }
}
