package de.servicehealth.epa4all.server.vsd;

import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSD;
import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.gematik.ws.conn.vsds.vsdservice.v5.VSDStatusType;
import de.health.service.cetp.IKonnektorClient;
import de.health.service.cetp.domain.eventservice.Subscription;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.serviceport.IKonnektorServicePortsAPI;
import de.servicehealth.epa4all.server.serviceport.MultiKonnektorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.xml.datatype.DatatypeFactory;
import java.io.ByteArrayOutputStream;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

@ApplicationScoped
public class VsdService {

    private static final Logger log = Logger.getLogger(VsdService.class.getName());

    private final MultiKonnektorService multiKonnektorService;
    private final IKonnektorClient konnektorClient;

    @Inject
    public VsdService(
        MultiKonnektorService multiKonnektorService,
        IKonnektorClient konnektorClient
    ) {
        this.multiKonnektorService = multiKonnektorService;
        this.konnektorClient = konnektorClient;
    }

    public static ReadVSDResponse buildSyntheticVSDResponse(String xml, byte[] bytes) throws Exception {
        ReadVSDResponse readVSDResponse = new ReadVSDResponse();
        readVSDResponse.setAllgemeineVersicherungsdaten(new byte[0]);
        readVSDResponse.setGeschuetzteVersichertendaten(new byte[0]);
        readVSDResponse.setPersoenlicheVersichertendaten(new byte[0]);

        byte[] gzipBytes;
        if (xml != null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            GZIPOutputStream os = new GZIPOutputStream(out);
            os.write(xml.getBytes());
            os.finish();

            gzipBytes = out.toByteArray();
        } else {
            gzipBytes = bytes;
        }
        readVSDResponse.setPruefungsnachweis(gzipBytes);

        VSDStatusType vsdStatus = new VSDStatusType();
        vsdStatus.setStatus("0");
        vsdStatus.setVersion("5.2.0");
        DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();
        GregorianCalendar gregorianCalendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        vsdStatus.setTimestamp(datatypeFactory.newXMLGregorianCalendar(gregorianCalendar));
        readVSDResponse.setVSDStatus(vsdStatus);

        return readVSDResponse;
    }

    public ReadVSDResponse readVsd(
        String egkHandle,
        String smcbHandle,
        UserRuntimeConfig runtimeConfig
    ) throws Exception {
        IKonnektorServicePortsAPI servicePorts = multiKonnektorService.getServicePorts(runtimeConfig);
        ContextType context = servicePorts.getContextType();
        if (context.getUserId() == null || context.getUserId().isEmpty()) {
            context.setUserId(UUID.randomUUID().toString());
        }
        String subsInfo = getSubscriptionsInfo(runtimeConfig);
        log.info(String.format(
            "readVSD for cardHandle=%s, smcbHandle=%s, subscriptions: %s", egkHandle, smcbHandle, subsInfo
        ));

        // TODO readEPrescriptionsMXBean.increaseVSDRead();

        ReadVSD readVSD = prepareReadVSDRequest(context, egkHandle, smcbHandle);
        return servicePorts.getVSDServicePortType().readVSD(readVSD);
    }

    private ReadVSD prepareReadVSDRequest(
        ContextType context,
        String egkHandle,
        String smcbHandle
    ) {
        ReadVSD readVSD = new ReadVSD();
        readVSD.setContext(context);
        readVSD.setEhcHandle(egkHandle);
        readVSD.setHpcHandle(smcbHandle);
        readVSD.setReadOnlineReceipt(true);
        readVSD.setPerformOnlineCheck(true);
        return readVSD;
    }

    private String getSubscriptionsInfo(UserRuntimeConfig runtimeConfig) {
        try {
            List<Subscription> subscriptions = konnektorClient.getSubscriptions(runtimeConfig);
            return subscriptions.stream()
                .map(s -> String.format("[id=%s eventTo=%s topic=%s]", s.getSubscriptionId(), s.getEventTo(), s.getTopic()))
                .collect(Collectors.joining(","));
        } catch (Throwable e) {
            log.log(Level.SEVERE, "Could not get active getSubscriptions", e);
            return "not available";
        }
    }
}
