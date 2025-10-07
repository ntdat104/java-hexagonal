package com.onemount.javahexagonal.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.onemount.javahexagonal.application.constant.ErrorConstant;
import com.onemount.javahexagonal.application.util.RequestContextUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Meta {

    public static final String SERVICE_CODE = "JAVA_HEXAGONAL";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String serviceCode;
    private String requestId;

    private Long timestamp;
    private String datetime;

    private Integer code;
    private String message;

    private Long total;

    private List<ErrorViolation> errors;

    public static Meta createSuccess() {
        return new Meta()
                .setServiceCode(SERVICE_CODE)
                .setRequestId(RequestContextUtils.getRequestIdFromAttribute())
                .setTimestamp(System.currentTimeMillis())
                .setDatetime(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                .setCode(ErrorConstant.SUCCESS)
                .setMessage("Success");
    }

    public static Meta createFailed(int code, String message, List<ErrorViolation> errors) {
        return new Meta()
                .setServiceCode(SERVICE_CODE)
                .setRequestId(RequestContextUtils.getRequestIdFromAttribute())
                .setTimestamp(System.currentTimeMillis())
                .setDatetime(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                .setCode(ErrorConstant.SUCCESS)
                .setMessage("Success")
                .setErrors(errors);
    }

    public static Meta createSuccess(Long total) {
        return new Meta()
                .setServiceCode(SERVICE_CODE)
                .setRequestId(RequestContextUtils.getRequestIdFromAttribute())
                .setTimestamp(System.currentTimeMillis())
                .setDatetime(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                .setCode(ErrorConstant.SUCCESS)
                .setMessage("Success")
                .setTotal(total);
    }
}
