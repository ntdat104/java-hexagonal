package com.onemount.javahexagonal.infrastructure.exception.handler;

import com.onemount.javahexagonal.application.constant.ErrorConstant;
import com.onemount.javahexagonal.application.dto.response.BaseResponse;
import com.onemount.javahexagonal.application.dto.response.ErrorViolation;
import com.onemount.javahexagonal.infrastructure.exception.BusinessError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.BindException;

import java.util.List;
import java.util.Optional;

@Component
public class ExceptionResolve {

    protected static Environment env;

    public ResponseEntity<BaseResponse<?>> resolveBindException(BindException exception) {
        List<ErrorViolation> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(e -> {
                    // Safely get the default message (e.g., "{400001}"), defaulting to a generic code
                    String rawKey = Optional.ofNullable(e.getDefaultMessage())
                            .orElse("{" + ErrorConstant.INVALID_PARAMETERS + "}"); // Fallback key

                    // Extract the pure error code (e.g., "400001") by stripping curly braces
                    String errorCode = rawKey.replaceAll("[{}]", "");

                    // Look up the full error message using the raw key (e.g., "{400001}")

                    return new ErrorViolation()
                            .setField(e.getField())
                            .setCode(errorCode) // Set the clean code
                            .setMessage(e.getDefaultMessage()); // Set the looked-up message
                })
                .toList();

        BusinessError error = BusinessError.getError(ErrorConstant.INVALID_PARAMETERS);
//        BaseResponse<?> data = ofFailed(error, getMessage(error), errors);
        return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
    }

    private int getErrorCode(String errorCode, int errorCodeDefault) {
        try {
            return Integer.parseInt(errorCode);
        } catch (NumberFormatException e) {
            return errorCodeDefault;
        }
    }

}
