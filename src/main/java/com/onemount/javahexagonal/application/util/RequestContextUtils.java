package com.onemount.javahexagonal.application.util;

import com.onemount.javahexagonal.application.constant.RequestKeyConstant;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
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

}
