package de.servicehealth.epa4all.serviceport;

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureServicePortType;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.CardServicePortType;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;

public class ServicePortAggregator implements IServicePortAggregator {

    private final ContextType contextType;
    private final CardServicePortType cardService;
    private final EventServicePortType eventService;
    private final CertificateServicePortType certificateService;
    private final AuthSignatureServicePortType authSignatureService;


    public ServicePortAggregator(
        ContextType contextType,
        CardServicePortType cardService,
        EventServicePortType eventService,
        CertificateServicePortType certificateService,
        AuthSignatureServicePortType authSignatureService
    ) {
        this.contextType = contextType;
        this.cardService = cardService;
        this.eventService = eventService;
        this.certificateService = certificateService;
        this.authSignatureService = authSignatureService;
    }

    @Override
    public ContextType getContextType() {
        return contextType;
    }

    @Override
    public CardServicePortType getCardService() {
        return cardService;
    }

    @Override
    public EventServicePortType getEventService() {
        return eventService;
    }

    @Override
    public CertificateServicePortType getCertificateService() {
        return certificateService;
    }

    @Override
    public AuthSignatureServicePortType getAuthSignatureService() {
        return authSignatureService;
    }
}
