package de.servicehealth.epa4all.server.logging;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.MDC;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("resource")
public class LogContext implements Closeable {

    private final Map<String, String> setWithPriorValues = new HashMap<>();

    private LogContext(Map<LogContextConstant, String> map) {
        for (Map.Entry<LogContextConstant, String> entry : map.entrySet()) {
            putMDC(entry.getKey(), entry.getValue());
        }
    }

    public static <T extends Exception> void withLogContext(
        Map<LogContextConstant, String> ctx,
        ThrowingRunnable<T> method
    ) throws T {
        try (LogContext ignored = new LogContext(ctx)) {
            method.run();
        }
    }

    public static Map<LogContextConstant, String> getMDCopyWithKnownConstantsOnly() {
        Map<String, String> mdcCopy = MDC.getCopyOfContextMap();
        if (mdcCopy == null) {
            return Map.of();
        }

        Map<String, LogContextConstant> reverseMap = Arrays.stream(LogContextConstant.values())
            .collect(Collectors.toMap(LogContextConstant::getIdentifier, con -> con));
        return mdcCopy.entrySet().stream()
            .filter(entry -> reverseMap.containsKey(entry.getKey()))
            .collect(Collectors.toMap(entry -> reverseMap.get(entry.getKey()), Map.Entry::getValue));
    }

    public LogContext with(LogContextConstant ctx, String value) {
        return putMDC(ctx, value);
    }

    /**
     * Allow to set MDC directly without our enum for custom fields
     */
    public LogContext with(String key, String value) {
        return putMDC(key, value);
    }

    private LogContext putMDC(LogContextConstant ctx, String value) {
        putMDC(ctx.getIdentifier(), value);
        return this;
    }

    private LogContext putMDC(String key, String value) {
        if (!setWithPriorValues.containsKey(key)) {
            setWithPriorValues.put(key, MDC.get(key));
        }
        MDC.put(key, value);
        return this;
    }

    @Override
    public void close() {
        for (Map.Entry<String, String> entry : setWithPriorValues.entrySet()) {
            if (entry.getValue() != null) {
                MDC.put(entry.getKey(), entry.getValue());
            } else {
                MDC.remove(entry.getKey());
            }
        }
    }

    /**
     * For our well-known-context variables in LogContextConstants, we support production anonymization.
     * Gematik wants to make sure that under no circumstances person-related-data is logged whereas in development
     * and integration testing, having as much data as possible makes debugging much easier. Hence, we support logging
     * of a lot of data but exclude it in production from the logs.
     */
    @VisibleForTesting
    String anonymize(LogContextConstant ctx, String value) {
        if (value == null) {
            return null;
        }

        return switch (ctx) {
            case SESSION_ID, PROTOCOL, JSON_MESSAGE_TYPE, WEBSOCKET_CONNECTION_ID, CONNECTION_KONNEKTOR -> value;
            case ICCSN -> anonymizeICCSN(value);
            case REMOTE_ADDR -> anonymizeRemoteAddr(value);
        };
    }

    @VisibleForTesting
    String anonymizeICCSN(@Nonnull String value) {
        // ICCSN is 20 digits
        // gemSpec_Karten_Fach_TIP_G2_1 hints that ICCSN is administrated by GS1 Germany GmbH in germany
        // GS1 published a document about ICCSN here: https://www.gs1-germany.de/fileadmin/gs1/basis_informationen/einheitliche_nummerierung_fuer_identifikationskarten_im_.pdf
        // Digit  1- 2: "Major Industry Identifier", for TI/healthcare: 80
        // Digit  3- 5: Country Code, for germany 276
        // Digit  6-10: "Kartenausgeberschlüssel", for eGK usually "Krankenkasse", non-personal related.
        // Digit 11-20: serial-number. unique per person => personal-related data
        if (value.length() == 20) {
            return value.substring(0, 10) + "*".repeat(10);
        }
        return "<REDACTED>";
    }

    @VisibleForTesting
    String anonymizeRemoteAddr(@Nonnull String value) {
        // The EuGH (Europäsche Gerichtshof) ruled that IP-addresses (static and dynamic) can be
        // considered personal-related data. Hence, we need to anonymize it, at least as long as it is not originating
        // from our internal networks (non-public ip address ranges)
        if (isPrivateIP(value)) {
            return value;
        }

        // Replace all digits (and IPv6 hex numbers) by X, keeping somewhat the form of e.g. XXX.XX.X.XXX,
        // Just in case we accidentally happen to not have an IP address in here, we also replace other chars like Q
        // by an X to be sure that everything is anonymized.
        return value.replaceAll("[0-9a-zA-Z]", "X");
    }

    public boolean isPrivateIP(String ipAddress) {
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);

            // Check if it's loopback address
            if (addr.isLoopbackAddress()) {
                return true;
            }

            // Check if it's a site-local address
            if (addr.isSiteLocalAddress()) {
                return true;
            }

            // Additional checks for IPv4
            if (addr instanceof Inet4Address) {
                return isPrivateIPv4(addr.getAddress());
            }

            // Additional checks for IPv6
            if (addr instanceof Inet6Address) {
                return isPrivateIPv6(addr.getAddress());
            }

        } catch (UnknownHostException e) {
            return false;
        }

        return false;
    }

    private boolean isPrivateIPv4(byte[] rawBytes) {
        // 10.0.0.0/8
        if (rawBytes[0] == 10) {
            return true;
        }
        // 172.16.0.0/12
        if (rawBytes[0] == (byte) 172 && (rawBytes[1] & 0xF0) == 16) {
            return true;
        }
        // 192.168.0.0/16
        if (rawBytes[0] == (byte) 192 && rawBytes[1] == (byte) 168) {
            return true;
        }
        return false;
    }

    private boolean isPrivateIPv6(byte[] rawBytes) {
        // fc00::/7 Unique local address
        return (rawBytes[0] & 0xfe) == 0xfc;
    }
}
