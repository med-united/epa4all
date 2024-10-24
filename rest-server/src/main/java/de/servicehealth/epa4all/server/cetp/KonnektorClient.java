package de.servicehealth.epa4all.server.cetp;

import de.gematik.ws.conn.connectorcommon.v5.Status;
import de.gematik.ws.conn.eventservice.v7.GetCards;
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
import de.health.service.cetp.domain.eventservice.card.CardsResponse;
import de.health.service.cetp.domain.fault.CetpFault;
import de.service.health.api.serviceport.IKonnektorServicePortsAPI;
import de.service.health.api.serviceport.MultiKonnektorService;
import de.servicehealth.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.cetp.mapper.card.CardTypeMapper;
import de.servicehealth.epa4all.server.cetp.mapper.card.CardsResponseMapper;
import de.servicehealth.epa4all.server.cetp.mapper.status.StatusMapper;
import de.servicehealth.epa4all.server.cetp.mapper.subscription.SubscriptionMapper;
import de.servicehealth.epa4all.server.cetp.mapper.subscription.SubscriptionResultMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.ws.Holder;

import javax.xml.datatype.XMLGregorianCalendar;
import java.util.List;
import java.util.stream.Collectors;

import static de.health.service.cetp.SubscriptionManager.FAILED;

@ApplicationScoped
public class KonnektorClient implements IKonnektorClient {

    private final Object emptyInput = new Object();

    private final MultiKonnektorService multiKonnektorService;

    private final SubscriptionResultMapper subscriptionResultMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final CardsResponseMapper cardsResponseMapper;
    private final CardTypeMapper cardTypeMapper;
    private final StatusMapper statusMapper;

    public KonnektorClient(
        MultiKonnektorService multiKonnektorService,
        SubscriptionResultMapper subscriptionResultMapper,
        SubscriptionMapper subscriptionMapper,
        CardsResponseMapper cardsResponseMapper,
        CardTypeMapper cardTypeMapper,
        StatusMapper statusMapper
    ) {
        this.multiKonnektorService = multiKonnektorService;
        this.subscriptionResultMapper = subscriptionResultMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.cardsResponseMapper = cardsResponseMapper;
        this.cardTypeMapper = cardTypeMapper;
        this.statusMapper = statusMapper;
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

    @Override
    public SubscriptionResult renewSubscription(UserRuntimeConfig runtimeConfig, String subscriptionId) throws CetpFault {
        IKonnektorServicePortsAPI servicePorts = multiKonnektorService.getServicePorts(runtimeConfig);
        EventServicePortType eventService = servicePorts.getEventService();

        Holder<Status> statusHolder = new Holder<>();
        Holder<RenewSubscriptionsResponse.SubscribeRenewals> renewalHolder = new Holder<>();

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
        IKonnektorServicePortsAPI servicePorts = multiKonnektorService.getServicePorts(runtimeConfig);
        EventServicePortType eventService = servicePorts.getEventService();
        GetCards getCards = new GetCards();
        getCards.setContext(servicePorts.getContextType());
        getCards.setCardType(cardTypeMapper.toSoap(cardType));
        try {
            CardsResponse cardsResponse = cardsResponseMapper.toDomain(eventService.getCards(getCards));
            return cardsResponse.getCards();
        } catch (FaultMessage faultMessage) {
            throw new CetpFault(faultMessage.getMessage());
        }
    }
}
