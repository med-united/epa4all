package de.servicehealth.logging;

import com.google.common.collect.Streams;
import de.servicehealth.utils.Action;
import de.servicehealth.utils.ActionEx;
import de.servicehealth.utils.ActionNr;
import de.servicehealth.utils.ActionWrNr;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.Closeable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@SuppressWarnings("resource")
public class LogContext implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(LogContext.class.getName());

    private static final Config CONFIG = ConfigProvider.getConfig();

    public static final String MASK_SENSITIVE = "servicehealth.client.mask-sensitive";
    public static final String MASKED_HEADERS = "servicehealth.client.masked-headers";
    public static final String MASKED_ATTRIBUTES = "servicehealth.client.masked-attributes";
    
    private static final boolean maskSensitive = CONFIG.getOptionalValue(MASK_SENSITIVE, Boolean.class).orElse(true);

    private static String[] getPropertyValuesArray(String property) {
        return CONFIG.getOptionalValue(property, String.class).orElse("").split(",");
    }

    private static final Set<String> maskedItems = new HashSet<>(
        Streams.concat(
            Stream.of(getPropertyValuesArray(MASKED_HEADERS)).map(String::valueOf),
            Stream.of(getPropertyValuesArray(MASKED_ATTRIBUTES)).map(String::valueOf)
        ).filter(s -> !s.isEmpty()).toList()
    );

    public static <T> T withMdc(Map<LogField, String> ctx, Action<T> action) {
        try (LogContext ignored = new LogContext(ctx)) {
            return action.execute();
        } 
    }

    public static <T> T withMdcEx(Map<LogField, String> ctx, ActionEx<T> action) throws Exception {
        try (LogContext ignored = new LogContext(ctx)) {
            return action.execute();
        }
    }

    public static void withMdcNr(Map<LogField, String> ctx, ActionNr action) {
        try (LogContext ignored = new LogContext(ctx)) {
            action.execute();
        }
    }

    public static void withMdcExNr(Map<LogField, String> ctx, ActionWrNr action) throws Exception {
        try (LogContext ignored = new LogContext(ctx)) {
            action.execute();
        }
    }

    private final Map<String, String> map = new HashMap<>();

    private LogContext putMDC(String key, String value) {
        if (!map.containsKey(key)) {
            map.put(key, MDC.get(key));
        }
        MDC.put(key, value);
        return this;
    }

    @Override
    public void close() {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            MDC.remove(entry.getKey());
        }
    }

    private LogContext(Map<LogField, String> map) {
        for (Map.Entry<LogField, String> entry : map.entrySet()) {
            putMDC(entry.getKey(), entry.getValue());
        }
    }

    public LogContext with(LogField ctx, String value) {
        return putMDC(ctx, value);
    }

    /**
     * Allow to set MDC directly without our enum for custom fields
     */
    public LogContext with(String key, String value) {
        return putMDC(key, value);
    }

    private LogContext putMDC(LogField ctx, String value) {
        String identifier = ctx.getIdentifier();
        putMDC(identifier, mask(identifier, value));
        return this;
    }

    String mask(String attribute, String value) {
        if (value == null) {
            return null;
        }
        return maskSensitive && maskedItems.contains(attribute) ? "XXX" : value;
    }
}
