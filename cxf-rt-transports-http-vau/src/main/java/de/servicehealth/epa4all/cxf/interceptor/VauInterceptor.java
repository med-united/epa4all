package de.servicehealth.epa4all.cxf.interceptor;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

public class VauInterceptor extends AbstractPhaseInterceptor<Message> {

    public VauInterceptor() {
        super(Phase.RECEIVE);
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        Integer responseCode = (Integer) message.get(Message.RESPONSE_CODE);
        System.out.println(responseCode);
    }
}
