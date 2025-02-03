package de.servicehealth.epa4all.server.logging;

import io.quarkus.logging.LoggingFilter;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

@LoggingFilter(name = "de.servicehealth.epa4all.server.logging.PublicLogFilter")
public class PublicLogFilter implements Filter {

    @Override
    public boolean isLoggable(LogRecord record) {
        return record.getMessage().startsWith("Error");
    }
}
