package com.onemount.javahexagonal.interfaces;

import com.onemount.javahexagonal.application.constant.UrlExternal;
import com.onemount.javahexagonal.application.dto.response.BaseResponse;
import com.onemount.javahexagonal.application.service.SystemService;
import com.onemount.javahexagonal.application.util.ContextUtils;
import com.onemount.javahexagonal.infrastructure.anotation.LogsActivity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @LogsActivity
    @GetMapping(UrlExternal.SYSTEM_TIME_PATH)
    public BaseResponse<?> getSystemTime() {
        return BaseResponse.ofSucceeded(systemService.getSystemTime());
    }

    @GetMapping("/greet")
    public String greet(HttpServletRequest request) {
        HttpServletRequest servletRequest = ContextUtils.getCurrentRequest();
        return "Welcome to Java Hexagonal "+request.getSession().getId();
    }
}
