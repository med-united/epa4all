package de.servicehealth.epa4all.cxf.interceptor;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.InterceptorChain;
import org.apache.cxf.message.Message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.TreeMap;

import static org.apache.cxf.message.Message.PROTOCOL_HEADERS;

public class InterceptorUtils {

    public static <T> boolean instanceOf(Interceptor<? extends Message> interceptor, Class<T> clazz) {
        return clazz.isInstance(interceptor);
    }

    @SuppressWarnings("rawtypes")
    public static InterceptorChain excludeInterceptors(Message message, Class... classes) {
        List<Interceptor<? extends Message>> excludedInterceptors = new ArrayList<>();
        InterceptorChain interceptorChain = message.getInterceptorChain();
        ListIterator<Interceptor<? extends Message>> chainIterator = interceptorChain.getIterator();
        while (chainIterator.hasNext()) {
            Interceptor<? extends Message> interceptor = chainIterator.next();
            if (Arrays.stream(classes).anyMatch(clazz -> instanceOf(interceptor, clazz))) {
                excludedInterceptors.add(interceptor);
            }
        }
        excludedInterceptors.forEach(interceptorChain::remove);
        return interceptorChain;
    }

    @SuppressWarnings("unchecked")
    public static List<Pair<String, String>> getProtocolHeaders(Message message) {
        TreeMap<String, Object> map = (TreeMap<String, Object>) message.get(PROTOCOL_HEADERS);
        return map.entrySet().stream()
            .map(e -> Pair.of(e.getKey(), ((List<String>) e.getValue()).getFirst()))
            .toList();
    }

    @SuppressWarnings("unchecked")
    public static void addProtocolHeader(Message message, String name, Object value) {
        TreeMap<String, Object> map = (TreeMap<String, Object>) message.get(PROTOCOL_HEADERS);
        map.put(name, List.of(value));
    }
}
