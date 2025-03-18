package de.servicehealth.epa4all.server.jcr.prop;

import javax.jcr.Value;
import javax.jcr.nodetype.PropertyDefinition;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

public interface MixinProp {

    String EPA_FLEX_FOLDER = "epa:flexFolder";

    String EPA_NAMESPACE_PREFIX = "epa";
    String EPA_NAMESPACE_URI = "https://www.service-health.de/epa";

    DateTimeFormatter LOCALDATE_YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    DateTimeFormatter LOCALDATE_YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    SimpleDateFormat DATE_YYYY_MM_DD = new SimpleDateFormat("yyyy-MM-dd");

    
    int getType();

    String getName();

    boolean isFulltext();

    boolean isMandatory();

    Function<Object, Value> getValueFunc();

    boolean equalTo(PropertyDefinition definition);
}