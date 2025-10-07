package com.onemount.javahexagonal.infrastructure.exception;

import lombok.Getter;
import lombok.Setter;
import org.springframework.core.env.Environment;
import org.springframework.util.ObjectUtils;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
public class BusinessError implements Serializable {

    protected static Environment env;

    @Serial
    private static final long serialVersionUID = 1L;

    private int code;
    private String message;

    public static BusinessError getError(String code) {
        BusinessError error = new BusinessError();
        error.setCode(Integer.parseInt(code));
        error.setMessage(getMessage(code));
        return error;
    }

    public static BusinessError getError(int code) {
        BusinessError error = new BusinessError();
        error.setCode(code);
        error.setMessage(getMessage(String.valueOf(code)));
        return error;
    }

    private static String getMessage(String key) {
        try {
            String result = env.getProperty(key);
            return ObjectUtils.isEmpty(result) ? key : result;
        } catch (Exception e) {
            return key;
        }
    }
}
