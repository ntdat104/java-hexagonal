package com.onemount.javahexagonal.interfaces;

import com.onemount.javahexagonal.application.constant.UrlExternal;
import com.onemount.javahexagonal.application.dto.response.BaseResponse;
import com.onemount.javahexagonal.application.service.SystemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @GetMapping(UrlExternal.SYSTEM_TIME_PATH)
    public BaseResponse<?> getSystemTime() {
        return BaseResponse.ofSucceeded(systemService.getSystemTime());
    }
}
