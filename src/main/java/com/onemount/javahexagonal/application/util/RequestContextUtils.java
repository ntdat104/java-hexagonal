package com.onemount.javahexagonal.application.util;

import com.onemount.javahexagonal.application.constant.RequestKeyConstant;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class RequestContextUtils {

    private RequestContextUtils() {}

    public static String getRequestIdFromMDC() {
        return MDC.get(RequestKeyConstant.X_REQUEST_ID);
    }

    public static String getRequestIdFromAttribute() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        return (String) request.getAttribute(RequestKeyConstant.X_REQUEST_ID);
    }

    public <T> void set(String key, T value) {
        RequestContextHolder.currentRequestAttributes()
                .setAttribute(key, value, RequestAttributes.SCOPE_REQUEST);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = RequestContextHolder.currentRequestAttributes()
                .getAttribute(key, RequestAttributes.SCOPE_REQUEST);
        if (value == null) return null;
        if (!type.isInstance(value)) {
            throw new IllegalStateException("Attribute " + key + " is not of type " + type.getName());
        }
        return (T) value;
    }

    public void clear(String key) {
        RequestContextHolder.currentRequestAttributes()
                .removeAttribute(key, RequestAttributes.SCOPE_REQUEST);
    }

}
