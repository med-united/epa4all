package de.servicehealth.epa4all.server.cetp;

import de.gematik.ws.conn.cardservice.v8.VerifyPin;
import de.gematik.ws.conn.certificateservice.v6.CryptType;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificate;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificateResponse;
import de.gematik.ws.conn.certificateservice.wsdl.v6_0.CertificateServicePortType;
import de.gematik.ws.conn.certificateservicecommon.v2.CertRefEnum;
import de.gematik.ws.conn.connectorcommon.v5.Status;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.v7.GetCardTerminals;
import de.gematik.ws.conn.eventservice.v7.GetCardTerminalsResponse;
import de.gematik.ws.conn.eventservice.v7.GetCards;
import de.gematik.ws.conn.eventservice.v7.GetCardsResponse;
import de.gematik.ws.conn.eventservice.v7.GetSubscription;
import de.gematik.ws.conn.eventservice.v7.GetSubscriptionResponse;
import de.gematik.ws.conn.eventservice.v7.RenewSubscriptions;
import de.gematik.ws.conn.eventservice.v7.RenewSubscriptionsResponse;
import de.gematik.ws.conn.eventservice.v7.Subscribe;
import de.gematik.ws.conn.eventservice.v7.SubscribeResponse;
import de.gematik.ws.conn.eventservice.v7.SubscriptionRenewal;
import de.gematik.ws.conn.eventservice.v7.SubscriptionType;
import de.gematik.ws.conn.eventservice.v7.Unsubscribe;
import de.gematik.ws.conn.eventservice.v7.UnsubscribeResponse;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.EventServicePortType;
import de.gematik.ws.conn.eventservice.wsdl.v7_2.FaultMessage;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.domain.CetpStatus;
import de.health.service.cetp.domain.SubscriptionResult;
import de.health.service.cetp.domain.eventservice.Subscription;
import de.health.service.cetp.domain.eventservice.card.Card;
import de.health.service.cetp.domain.eventservice.card.CardType;
import de.health.service.cetp.domain.eventservice.cardTerminal.CardTerminal;
import de.health.service.cetp.domain.fault.CetpFault;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.cetp.mapper.card.CardTypeMapper;
import de.servicehealth.epa4all.server.cetp.mapper.card.CardsResponseMapper;
import de.servicehealth.epa4all.server.cetp.mapper.cardTerminal.CardTerminalsResponseMapper;
import de.servicehealth.epa4all.server.cetp.mapper.status.StatusMapper;
import de.servicehealth.epa4all.server.cetp.mapper.subscription.SubscriptionMapper;
import de.servicehealth.epa4all.server.cetp.mapper.subscription.SubscriptionResultMapper;
import de.servicehealth.epa4all.server.serviceport.IKonnektorServicePortsAPI;
import de.servicehealth.epa4all.server.serviceport.KonnektorKey;
import de.servicehealth.epa4all.server.serviceport.MultiKonnektorService;
import de.servicehealth.epa4all.server.vsd.VsdConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.ws.Holder;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import javax.xml.datatype.XMLGregorianCalendar;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static de.gematik.ws.conn.certificateservice.v6.CryptType.RSA;
import static de.health.service.cetp.SubscriptionManager.FAILED;
import static de.health.service.cetp.domain.eventservice.card.CardType.EGK;
import static de.health.service.cetp.domain.eventservice.card.CardType.SMC_B;
import static de.servicehealth.epa4all.server.idp.IdpClient.BOUNCY_CASTLE_PROVIDER;
import static de.servicehealth.utils.SSLUtils.extractTelematikIdFromCertificate;

@ApplicationScoped
public class KonnektorClient implements IKonnektorClient {

    private final ConcurrentHashMap<KonnektorKey, String> runtimeConfigSmcbMap = new ConcurrentHashMap<>();

    @Getter
    private final ConcurrentHashMap<String, String> smcbTelematikMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, X509Certificate> smcbCertificateMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> egkMap = new ConcurrentHashMap<>();

    private final Object emptyInput = new Object();

    private final MultiKonnektorService multiKonnektorService;

    private final SubscriptionResultMapper subscriptionResultMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final CardsResponseMapper cardsResponseMapper;
    private final CardTerminalsResponseMapper cardTerminalsResponseMapper;
    private final CardTypeMapper cardTypeMapper;
    private final StatusMapper statusMapper;
    private final VsdConfig vsdConfig;

    public KonnektorClient(
        MultiKonnektorService multiKonnektorService,
        SubscriptionResultMapper subscriptionResultMapper,
        SubscriptionMapper subscriptionMapper,
        CardsResponseMapper cardsResponseMapper,
        CardTerminalsResponseMapper cardTerminalsResponseMapper,
        CardTypeMapper cardTypeMapper,
        StatusMapper statusMapper,
        VsdConfig vsdConfig
    ) {
        this.multiKonnektorService = multiKonnektorService;
        this.subscriptionResultMapper = subscriptionResultMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.cardsResponseMapper = cardsResponseMapper;
        this.cardTerminalsResponseMapper = cardTerminalsResponseMapper;
        this.cardTypeMapper = cardTypeMapper;
        this.statusMapper = statusMapper;
        this.vsdConfig = vsdConfig;
    }

    @Override
    public List<Subscription> getSubscriptions(UserRuntimeConfig runtimeConfig) throws CetpFault {
        IKonnektorServicePortsAPI servicePorts = multiKonnektorService.getServicePorts(runtimeConfig);
        EventServicePortType eventService = servicePorts.getEventService();
        GetSubscription getSubscriptionRequest = new GetSubscription();
        getSubscriptionRequest.setContext(servicePorts.getContextType());
        getSubscriptionRequest.setMandantWide(false);

        try {
            GetSubscriptionResponse subscription = eventService.getSubscription(getSubscriptionRequest);
            return subscription.getSubscriptions().getSubscription()
                .stream().map(subscriptionMapper::toDomain)
                .collect(Collectors.toList());

        } catch (FaultMessage faultMessage) {
            throw new CetpFault(faultMessage.getMessage());
        }
    }

    public String getTelematikId(UserRuntimeConfig userRuntimeConfig, String smcbHandle) {
        return smcbTelematikMap.computeIfAbsent(smcbHandle, handle -> {
            X509Certificate x509Certificate = getSmcbX509Certificate(userRuntimeConfig, smcbHandle);
            return extractTelematikIdFromCertificate(x509Certificate);
        });
    }

    public String getSmcbHandle(UserRuntimeConfig userRuntimeConfig) throws CetpFault {
        return computeIfAbsentCetpEx(
            runtimeConfigSmcbMap,
            new KonnektorKey(userRuntimeConfig),
            userRuntimeConfig,
            (key, config) -> {
                List<Card> cards = getCards(config, SMC_B);
                if (vsdConfig.isHandlesTestMode()) {
                    Optional<Card> cardOpt = cards.stream()
                        .filter(c -> vsdConfig.getTestSmcbCardholderName().equals(c.getCardHolderName()))
                        .findAny();
                    return cardOpt.map(Card::getCardHandle).orElse(cards.getFirst().getCardHandle());
                } else {
                    String primaryIccsn = vsdConfig.getPrimaryIccsn();
                    return cards.stream()
                        .filter(c -> primaryIccsn.equals(c.getIccsn()))
                        .findAny()
                        .map(Card::getCardHandle)
                        .orElseThrow(() -> new CetpFault(String.format("Could not get SMC-B card for ICCSN: %s", primaryIccsn)));
                }
            });
    }

    public String getKvnr(UserRuntimeConfig userRuntimeConfig, String egkHandle) throws CetpFault {
        List<Card> cards = getCards(userRuntimeConfig, EGK);
        return cards.stream()
            .filter(c -> c.getCardHandle().equals(egkHandle))
            .findAny()
            .map(Card::getKvnr)
            .orElseThrow(() -> new CetpFault(String.format("Could not get KVNR for EGK=%s", egkHandle)));
    }

    public String getEgkHandle(UserRuntimeConfig userRuntimeConfig, String insurantId) throws CetpFault {
        return computeIfAbsentCetpEx(
            egkMap,
            insurantId,
            userRuntimeConfig,
            (key, config) -> {
                List<Card> cards = getCards(config, EGK);
                Optional<Card> card = cards.stream().filter(c -> c.getKvnr().equals(insurantId)).findAny();
                return vsdConfig.isHandlesTestMode()
                    ? card.map(Card::getCardHandle).orElse(cards.getFirst().getCardHandle())
                    : card.map(Card::getCardHandle).orElseThrow(() ->
                    new CetpFault(String.format("Could not get EGK card for insurantId: %s", insurantId))
                );
            }
        );
    }

    @Override
    public SubscriptionResult renewSubscription(UserRuntimeConfig runtimeConfig, String subscriptionId) throws CetpFault {
        IKonnektorServicePortsAPI servicePorts = multiKonnektorService.getServicePorts(runtimeConfig);
        EventServicePortType eventService = servicePorts.getEventService();

        Holder<Status> statusHolder = new Holder<>();

        RenewSubscriptions renewSubscriptions = new RenewSubscriptions();
        renewSubscriptions.setContext(servicePorts.getContextType());
        renewSubscriptions.getSubscriptionID().add(subscriptionId);
        try {
            RenewSubscriptionsResponse renewedSubscriptions = eventService.renewSubscriptions(renewSubscriptions);
            SubscriptionRenewal subscriptionRenewal = renewedSubscriptions.getSubscribeRenewals().getSubscriptionRenewal().getFirst();
            return subscriptionResultMapper.toDomain(subscriptionRenewal, statusHolder);
        } catch (FaultMessage faultMessage) {
            throw new CetpFault(faultMessage.getMessage());
        }
    }

    @Override
    public SubscriptionResult subscribe(UserRuntimeConfig runtimeConfig, String cetpHost) throws CetpFault {
        IKonnektorServicePortsAPI servicePorts = multiKonnektorService.getServicePorts(runtimeConfig);
        EventServicePortType eventService = servicePorts.getEventService();

        Subscribe subscribe = new Subscribe();
        subscribe.setContext(servicePorts.getContextType());

        SubscriptionType subscriptionType = new SubscriptionType();
        subscriptionType.setEventTo(cetpHost);
        subscriptionType.setTopic(CARD_INSERTED_TOPIC);
        subscribe.setSubscription(subscriptionType);
        try {
            SubscribeResponse subscribeResponse = eventService.subscribe(subscribe);
            Status status = subscribeResponse.getStatus();
            String subscriptionId = subscribeResponse.getSubscriptionID();
            XMLGregorianCalendar terminationTime = subscribeResponse.getTerminationTime();
            return subscriptionResultMapper.toDomain(emptyInput, status, subscriptionId, terminationTime);
        } catch (FaultMessage faultMessage) {
            throw new CetpFault(faultMessage.getMessage());
        }
    }

    @Override
    public CetpStatus unsubscribe(UserRuntimeConfig runtimeConfig, String subscriptionId, String cetpHost, boolean forceCetp) throws CetpFault {
        IKonnektorServicePortsAPI servicePorts = multiKonnektorService.getServicePorts(runtimeConfig);
        EventServicePortType eventService = servicePorts.getEventService();
        try {
            Unsubscribe unsubscribe = new Unsubscribe();
            unsubscribe.setContext(servicePorts.getContextType());
            if (forceCetp) {
                unsubscribe.setEventTo(cetpHost);
                UnsubscribeResponse unsubscribeResponse = eventService.unsubscribe(unsubscribe);
                return statusMapper.toDomain(unsubscribeResponse.getStatus());
            } else {
                if (subscriptionId == null || subscriptionId.startsWith(FAILED)) {
                    CetpStatus status = new CetpStatus();
                    status.setResult("Previous subscription is not found");
                    return status;
                }
                unsubscribe.setSubscriptionID(subscriptionId);
                UnsubscribeResponse unsubscribeResponse = eventService.unsubscribe(unsubscribe);
                return statusMapper.toDomain(unsubscribeResponse.getStatus());
            }
        } catch (FaultMessage faultMessage) {
            throw new CetpFault(faultMessage.getMessage());
        }
    }

    @Override
    public List<Card> getCards(UserRuntimeConfig runtimeConfig, CardType cardType) throws CetpFault {
        GetCardsResponse cardsResponse = getCardsResponse(runtimeConfig, cardType);
        return cardsResponseMapper.toDomain(cardsResponse).getCards();
    }

    public GetCardsResponse getCardsResponse(UserRuntimeConfig runtimeConfig, CardType cardType) throws CetpFault {
        IKonnektorServicePortsAPI servicePorts = multiKonnektorService.getServicePorts(runtimeConfig);
        EventServicePortType eventService = servicePorts.getEventService();
        GetCards getCards = new GetCards();
        getCards.setContext(servicePorts.getContextType());
        getCards.setCardType(cardType == null ? null : cardTypeMapper.toSoap(cardType));
        try {
            return eventService.getCards(getCards);
        } catch (Exception e) {
            throw new CetpFault(e.getMessage());
        }
    }

    @Override
    public List<CardTerminal> getCardTerminals(UserRuntimeConfig runtimeConfig) throws CetpFault {
        GetCardTerminalsResponse cardTerminalsResponse = getCardTerminalsResponse(runtimeConfig);
        return cardTerminalsResponseMapper.toDomain(cardTerminalsResponse).getCardTerminals();
    }

    public GetCardTerminalsResponse getCardTerminalsResponse(UserRuntimeConfig runtimeConfig) throws CetpFault {
        IKonnektorServicePortsAPI servicePorts = multiKonnektorService.getServicePorts(runtimeConfig);
        EventServicePortType eventService = servicePorts.getEventService();
        GetCardTerminals getCardTerminals = new GetCardTerminals();
        getCardTerminals.setContext(servicePorts.getContextType());
        try {
            return eventService.getCardTerminals(getCardTerminals);
        } catch (Exception e) {
            throw new CetpFault(e.getMessage());
        }
    }

    @Override
    public X509Certificate getSmcbX509Certificate(UserRuntimeConfig userRuntimeConfig, String smcbHandle) {
        return smcbCertificateMap.computeIfAbsent(smcbHandle, (handle) -> {
            IKonnektorServicePortsAPI servicePorts = multiKonnektorService.getServicePorts(userRuntimeConfig);
            return getSmcbX509Certificate(servicePorts, smcbHandle).getKey();
        });
    }

    public Pair<X509Certificate, Boolean> getSmcbX509Certificate(IKonnektorServicePortsAPI servicePorts, String smcbHandle) {
        Pair<ReadCardCertificateResponse, Boolean> certResponsePair = getReadCardCertificateResponse(servicePorts, smcbHandle);
        if (certResponsePair == null) {
            throw new RuntimeException("Could not read card certificate");
        }
        byte[] x509Certificate = certResponsePair.getKey()
            .getX509DataInfoList()
            .getX509DataInfo()
            .getFirst()
            .getX509Data()
            .getX509Certificate();

        X509Certificate certBytes = getCertificateFromAsn1DERCertBytes(x509Certificate);
        Boolean signatureChanged = certResponsePair.getValue();
        return Pair.of(certBytes, signatureChanged);
    }

    private Pair<ReadCardCertificateResponse, Boolean> getReadCardCertificateResponse(
        IKonnektorServicePortsAPI servicePorts,
        String smcbHandle
    ) {
        // A_20666-02 - Auslesen des Authentisierungszertifikates
        ReadCardCertificate readCardCertificateRequest = new ReadCardCertificate();
        ReadCardCertificate.CertRefList certRefList = new ReadCardCertificate.CertRefList();
        certRefList.getCertRef().add(CertRefEnum.C_AUT);
        readCardCertificateRequest.setCertRefList(certRefList);
        readCardCertificateRequest.setCardHandle(smcbHandle);

        ContextType contextType = servicePorts.getContextType();
        readCardCertificateRequest.setContext(contextType);

        CertificateServicePortType certificateService = servicePorts.getCertificateService();
        try {
            readCardCertificateRequest.setCrypt(CryptType.ECC);
            return Pair.of(certificateService.readCardCertificate(readCardCertificateRequest), false);
        } catch (de.gematik.ws.conn.certificateservice.wsdl.v6_0.FaultMessage e) {
            // Zugriffsbedingungen nicht erfüllt
            boolean code4085 = e.getFaultInfo().getTrace().stream().anyMatch(t ->
                t.getCode().equals(BigInteger.valueOf(4085L))
            );
            if (code4085) {
                try {
                    verifyPin(servicePorts, smcbHandle);
                    // try again
                    getReadCardCertificateResponse(servicePorts, smcbHandle);
                } catch (de.gematik.ws.conn.cardservice.wsdl.v8_1.FaultMessage e2) {
                    throw new RuntimeException("Could not verify pin", e2);
                }
            } else {
                try {
                    readCardCertificateRequest.setCrypt(RSA);
                    ReadCardCertificateResponse response = certificateService.readCardCertificate(readCardCertificateRequest);

                    return Pair.of(response, true);
                } catch (de.gematik.ws.conn.certificateservice.wsdl.v6_0.FaultMessage e1) {
                    throw new RuntimeException("Could not external authenticate", e1);
                }
            }
        }
        return null;
    }

    private void verifyPin(IKonnektorServicePortsAPI servicePorts, String smcbHandle) throws de.gematik.ws.conn.cardservice.wsdl.v8_1.FaultMessage {
        VerifyPin verifyPin = new VerifyPin();
        verifyPin.setContext(servicePorts.getContextType());
        verifyPin.setCardHandle(smcbHandle);
        verifyPin.setPinTyp("PIN.SMC");
        servicePorts.getCardService().verifyPin(verifyPin);
    }

    private static X509Certificate getCertificateFromAsn1DERCertBytes(final byte[] crt) {
        try (InputStream in = new ByteArrayInputStream(crt)) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509", BOUNCY_CASTLE_PROVIDER);
            return (X509Certificate) certFactory.generateCertificate(in);
        } catch (IOException | CertificateException e) {
            throw new RuntimeException(e);
        }
    }
}
