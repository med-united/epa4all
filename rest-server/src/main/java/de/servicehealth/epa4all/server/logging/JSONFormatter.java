package de.servicehealth.epa4all.server.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.MDC;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

/**
 * A {@code java.util.logging.Formatter} for java.util.logging that logs
 * messages as JSON
 */
public class JSONFormatter extends Formatter {

    private static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();

    /**
     * Pattern for the logged time which is in ISO 8601 format
     */
    public static final DateTimeFormatter ISO_8601_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        .withZone(ZoneId.systemDefault());

    /**
     * JSON key for log time
     */
    public static final String KEY_TIMESTAMP = "timestamp";

    /**
     * JSON key for logger name
     */
    public static final String KEY_LOGGER_NAME = "loggerName";

    /**
     * JSON key for log level
     */
    public static final String KEY_LOG_LEVEL = "level";

    /**
     * JSON key for thread name that issued the log statement
     */
    public static final String KEY_THREAD_NAME = "threadName";

    /**
     * JSON key for class name that issued the log statement
     */
    public static final String KEY_LOGGER_CLASS = "class";

    /**
     * JSON key for method name that issued the log statement
     */
    public static final String KEY_LOGGER_METHOD = "method";

    /**
     * JSON key for the message being logged
     */
    public static final String KEY_MESSAGE = "message";

    /**
     * JSON key for the exception being logged
     */
    public static final String KEY_EXCEPTION = "exception";

    /**
     * JSON key for the mdc being logged
     */
    public static final String KEY_MDC = "mdc";

    /**
     * JSON keys used for fields within the exception object
     */
    public enum ExceptionKeys {
        exceptionType, exceptionMessage, stackTrace // Keep in sync with ere-ps-app naming
    }

    /**
     * Cache of thread names
     */
    private static final Map<Integer, String> THREAD_NAME_CACHE = new LinkedHashMap<Integer, String>();

    private final boolean useSlf4jLevelNames;
    private final String timestampKey;
    private final String loggerNameKey;
    private final String logLevelKey;
    private final String threadNameKey;
    private final String loggerClassKey;
    private final String loggerMethodKey;
    private final String messageKey;
    private final String exceptionKey;
    private final String mdcKey;

    /**
     * If set, allows to include only certain fields in the MDC.
     */
    private final Set<String> mdcIncludeFields;

    /**
     * Wished by operations: Include MDC only for certain log levels, e.g. if MDC contains personal-related-data,
     * make sure that it is only included in ERROR logs that is for exceptions logged to identify which account has
     * problems or something like that. Also allows to e.g. only have DEBUG logs contain MDC if some bug must be debugged
     * and additional information are needed.
     */
    private final Set<String> mdcIncludeLogLevel;

    public JSONFormatter() {
        this(
            getBooleanProperty("use_slf4j_level_names"),
            getStringProperty("key_timestamp", KEY_TIMESTAMP),
            getStringProperty("key_logger_name", KEY_LOGGER_NAME),
            getStringProperty("key_log_level", KEY_LOG_LEVEL),
            getStringProperty("key_thread_name", KEY_THREAD_NAME),
            getStringProperty("key_logger_class", KEY_LOGGER_CLASS),
            getStringProperty("key_logger_method", KEY_LOGGER_METHOD),
            getStringProperty("key_message", KEY_MESSAGE),
            getStringProperty("key_exception", KEY_EXCEPTION),
            getStringProperty("key_mdc", KEY_MDC),
            configStrToSet(getStringProperty("mdc_include_fields", null)),
            configStrToSet(getStringProperty("mdc_include_log_level", null))
        );
    }

    @VisibleForTesting
    JSONFormatter(
        Boolean useSlf4jLevelNames,
        String timestampKey,
        String loggerNameKey,
        String logLevelKey,
        String threadNameKey,
        String loggerClassKey,
        String loggerMethodKey,
        String messageKey,
        String exceptionKey,
        String mdcKey,
        Set<String> mdcIncludeFields,
        Set<String> mdcIncludeLogLevel
    ) {
        this.useSlf4jLevelNames = useSlf4jLevelNames;
        this.timestampKey = timestampKey;
        this.loggerNameKey = loggerNameKey;
        this.logLevelKey = logLevelKey;
        this.threadNameKey = threadNameKey;
        this.loggerClassKey = loggerClassKey;
        this.loggerMethodKey = loggerMethodKey;
        this.messageKey = messageKey;
        this.exceptionKey = exceptionKey;
        this.mdcKey = mdcKey;
        this.mdcIncludeFields = mdcIncludeFields;
        this.mdcIncludeLogLevel = mdcIncludeLogLevel;
    }

    @Override
    public String format(LogRecord record) {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put(timestampKey, ISO_8601_FORMAT.format(Instant.ofEpochMilli(record.getMillis())));
        String loglevel = record.getLevel().getName();
        if (useSlf4jLevelNames) {
            object.put(logLevelKey, renameLogLevel(loglevel));
        } else {
            object.put(logLevelKey, loglevel);
        }
        object.put(threadNameKey, getThreadName(record.getThreadID()));

        object.put(messageKey, formatMessage(record));

        object.put(loggerNameKey, record.getLoggerName());

        if (null != record.getSourceClassName()) {
            object.put(loggerClassKey, record.getSourceClassName());
        }

        if (null != record.getSourceMethodName()) {
            object.put(loggerMethodKey, record.getSourceMethodName());
        }

        // Used an enum map for lighter memory consumption
        if (null != record.getThrown()) {
            Map<ExceptionKeys, Object> exceptionInfo = new EnumMap<>(ExceptionKeys.class);
            exceptionInfo.put(ExceptionKeys.exceptionType, record.getThrown().getClass().getName());

            if (record.getThrown().getMessage() != null) {
                exceptionInfo.put(ExceptionKeys.exceptionMessage, record.getThrown().getMessage());
            }

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            record.getThrown().printStackTrace(pw);
            pw.close();
            exceptionInfo.put(ExceptionKeys.stackTrace, sw.toString());
            object.put(exceptionKey, exceptionInfo);
        }

        handleMDC(object, loglevel);

        return convertToJson(object);
    }

    private void handleMDC(Map<String, Object> logObject, String logLevel) {
        if (mdcIncludeLogLevel != null && (!mdcIncludeLogLevel.contains("ALL") && (!mdcIncludeLogLevel.contains(logLevel)))) {
            // If includeLogLevel is set, it must either include the "ALL" to keep all levels or the specific level
            // otherwise, we don't include the MDC in the log message here.
            return;
        }
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        if (contextMap != null && !contextMap.isEmpty()) {
            if ((mdcIncludeFields != null) && !mdcIncludeFields.isEmpty()) {
                // Keep only those fields that are in the "includeFields", if the property is set at all (If not set, we keep all)
                contextMap.keySet().retainAll(mdcIncludeFields);
            }
            logObject.put(mdcKey, contextMap);
        }
    }

    @VisibleForTesting
    static Set<String> configStrToSet(String configStr) {
        if (configStr == null) {
            return null;
        }

        // Comma separated list of config parameters to set of parameters
        return Arrays.stream(configStr.split(",")).map(String::trim).collect(Collectors.toSet());
    }

    /**
     * Gets the thread name from the threadId present in the logRecord.
     */
    private static String getThreadName(int logRecordThreadId) {
        String result = THREAD_NAME_CACHE.get(logRecordThreadId);

        if (result != null) {
            return result;
        }

        if (logRecordThreadId > Integer.MAX_VALUE / 2) {
            result = String.valueOf(logRecordThreadId);
        } else {
            ThreadInfo threadInfo = THREAD_MX_BEAN.getThreadInfo(logRecordThreadId);
            if (threadInfo == null) {
                return String.valueOf(logRecordThreadId);
            }
            result = threadInfo.getThreadName();
        }

        synchronized (THREAD_NAME_CACHE) {
            THREAD_NAME_CACHE.put(logRecordThreadId, result);
        }

        return result;
    }

    private static String getStringProperty(String key, String defaultValue) {
        String val = getProperty(key);
        if (val == null) {
            return defaultValue;
        }
        return val.trim();
    }

    private static boolean getBooleanProperty(String key) {
        return Boolean.parseBoolean(getProperty(key));
    }

    private static String getProperty(String key) {
        return LogManager.getLogManager().getProperty(JSONFormatter.class.getName() + "." + key);
    }

    /**
     * JSON converter
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String convertToJson(Map<String, Object> map) {
        try {
            return MAPPER.writeValueAsString(map) + System.lineSeparator();
        } catch (JsonProcessingException e) {
            return map.toString();
        }
    }

    /**
     * Rename log levels to
     * <a href="http://www.slf4j.org/apidocs/org/slf4j/bridge/SLF4JBridgeHandler.html">...</a>
     *
     * <pre>
     * FINEST  -&gt; TRACE
     * FINER   -&gt; DEBUG
     * FINE    -&gt; DEBUG
     * INFO    -&gt; INFO
     * CONFIG  -&gt; CONFIG
     * WARNING -&gt; WARN
     * SEVERE  -&gt; ERROR
     * </pre>
     *
     */
    private String renameLogLevel(String logLevel) {

        switch (logLevel) {
            case "FINEST":
                return "TRACE";

            case "FINER":
            case "FINE":
                return "DEBUG";

            case "INFO":
            case "CONFIG":
                return "INFO";

            case "WARNING":
                return "WARN";

            case "SEVERE":
                return "ERROR";

            default:
                return logLevel;
        }
    }
}