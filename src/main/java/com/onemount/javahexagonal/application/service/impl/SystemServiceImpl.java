package com.onemount.javahexagonal.application.service.impl;

import com.onemount.javahexagonal.application.dto.response.SystemTimeDto;
import com.onemount.javahexagonal.application.service.SystemService;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class SystemServiceImpl implements SystemService {

    @Override
    public SystemTimeDto getSystemTime() {
        Date date = new Date();
        SystemTimeDto result = new SystemTimeDto();
        result.setTimestamp(date.getTime());
        result.setDatetime(date.toString());
        return result;
    }
}
