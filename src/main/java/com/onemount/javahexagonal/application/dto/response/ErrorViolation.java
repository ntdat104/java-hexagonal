package com.onemount.javahexagonal.application.dto.response;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ErrorViolation {

    private String field;
    private String code;
    private String message;

}
