package de.servicehealth.epa4all.cxf.interceptor;

import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.InterceptorChain;
import org.apache.cxf.message.Message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

public class InterceptorUtils {

    public static <T> boolean check(Interceptor<? extends Message> interceptor, Class<T> clazz) {
        return clazz.isInstance(interceptor);
    }

    @SuppressWarnings("rawtypes")
    public static InterceptorChain excludeInterceptors(Message message, Class... classes) {
        List<Interceptor<? extends Message>> excludedInterceptors = new ArrayList<>();
        InterceptorChain interceptorChain = message.getInterceptorChain();
        ListIterator<Interceptor<? extends Message>> chainIterator = interceptorChain.getIterator();
        while (chainIterator.hasNext()) {
            Interceptor<? extends Message> interceptor = chainIterator.next();
            if (Arrays.stream(classes).anyMatch(clazz -> check(interceptor, clazz))) {
                excludedInterceptors.add(interceptor);
            }
        }
        excludedInterceptors.forEach(interceptorChain::remove);
        return interceptorChain;
    }
}
