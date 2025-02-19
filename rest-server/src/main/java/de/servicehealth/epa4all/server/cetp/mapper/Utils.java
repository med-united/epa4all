package de.servicehealth.epa4all.server.cetp.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.core.Response;

import javax.xml.datatype.XMLGregorianCalendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import static de.servicehealth.utils.ServerUtils.extractJsonNode;

@SuppressWarnings("unused")
public class Utils {

    public static Date calendarToDate(XMLGregorianCalendar calendar) {
        return calendar.toGregorianCalendar().getTime();
    }

    public static List<JsonNode> getPayloads(Collection<Response> responses) {
        return responses.stream().map(r -> extractJsonNode(r.getEntity())).toList();
    }
}