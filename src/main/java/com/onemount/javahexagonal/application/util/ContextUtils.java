package com.onemount.javahexagonal.application.util;

import com.onemount.javahexagonal.application.constant.RequestKeyConstant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class ContextUtils {

    public static HttpServletRequest getCurrentRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            return ((ServletRequestAttributes) requestAttributes).getRequest();
        }
        return null;
    }

    public String getRequestId() {
        HttpServletRequest currentRequest = getCurrentRequest();
        if (currentRequest == null) {
            return null;
        }
        return (String) currentRequest.getAttribute(RequestKeyConstant.X_REQUEST_ID);
    }

}
