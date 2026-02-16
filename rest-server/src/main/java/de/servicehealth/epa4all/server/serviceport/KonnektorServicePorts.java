package de.servicehealth.epa4all.server.serviceport;

import de.gematik.ws.conn.authsignatureservice.wsdl.v7_4.AuthSignatureServicePortType;
import de.gematik.ws.conn.cardservice.wsdl.v8_1.CardServicePortType;
import de.gematik.ws.conn.cardterminalservice.wsdl.v1_1.CardTerminalServicePortType;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;
import de.gematik.ws.conn.vsds.vsdservice.v5_2.VSDServicePortType;

public class KonnektorServicePorts implements IKonnektorAPI {

    private final ContextType contextType;
    private final CardServicePortType cardService;
    private final EventServicePortType eventServiceSilent;
    private final EventServicePortType eventService;
    private final VSDServicePortType vsdServicePortType;
    private final CertificateServicePortType certificateService;
    private final AuthSignatureServicePortType authSignatureService;
    private final CardTerminalServicePortType cardTerminalServicePortType;

    public KonnektorServicePorts(
        ContextType contextType,
        CardServicePortType cardService,
        EventServicePortType eventService,
        EventServicePortType eventServiceSilent,
        VSDServicePortType vsdServicePortType,
        CertificateServicePortType certificateService,
        AuthSignatureServicePortType authSignatureService,
        CardTerminalServicePortType cardTerminalServicePortType
    ) {
        this.contextType = contextType;
        this.cardService = cardService;
        this.eventService = eventService;
        this.eventServiceSilent = eventServiceSilent;
        this.vsdServicePortType = vsdServicePortType;
        this.certificateService = certificateService;
        this.authSignatureService = authSignatureService;
        this.cardTerminalServicePortType = cardTerminalServicePortType;
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
    public EventServicePortType getEventServiceSilent() {
        return eventServiceSilent;
    }

    @Override
    public EventServicePortType getEventService() {
        return eventService;
    }

    @Override
    public VSDServicePortType getVSDServicePortType() {
        return vsdServicePortType;
    }

    @Override
    public CertificateServicePortType getCertificateService() {
        return certificateService;
    }

    @Override
    public CardTerminalServicePortType getCardTerminalService() {
        return cardTerminalServicePortType;
    }

    @Override
    public AuthSignatureServicePortType getAuthSignatureService() {
        return authSignatureService;
    }
}
