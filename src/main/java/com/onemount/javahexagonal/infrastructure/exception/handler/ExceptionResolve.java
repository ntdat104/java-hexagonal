package com.onemount.javahexagonal.infrastructure.exception.handler;

import com.onemount.javahexagonal.application.constant.ErrorConstant;
import com.onemount.javahexagonal.application.dto.response.BaseResponse;
import com.onemount.javahexagonal.application.dto.response.ErrorViolation;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ExceptionResolve {

    private final MessageSource messageSource;

    public ResponseEntity<BaseResponse<?>> resolveBindException(BindException exception) {
        // Lấy Locale hiện tại từ request (Header Accept-Language)
        Locale locale = LocaleContextHolder.getLocale();

        List<ErrorViolation> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(e -> {
                    var defaultCode = ErrorConstant.INVALID_PARAMETERS;
                    String rawCode = Optional.ofNullable(e.getDefaultMessage())
                            .orElse(String.valueOf(defaultCode));
                    int errorCode = parseErrorCode(rawCode, defaultCode);
                    return new ErrorViolation()
                            .setField(e.getField())
                            .setRejectedValue(e.getRejectedValue())
                            .setCode(errorCode)
                            .setMessage(getErrorMessage(errorCode, locale));
                })
                .sorted(Comparator.comparingInt(ErrorViolation::getCode))
                .toList();
        var defaultCode = ErrorConstant.INVALID_PARAMETERS;
        BaseResponse<?> error = BaseResponse.ofFailed(defaultCode, getErrorMessage(defaultCode, locale), errors);
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    private int parseErrorCode(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String getErrorMessage(int code, Locale locale) {
        return getErrorMessage(String.valueOf(code), locale);
    }

    private String getErrorMessage(String code, Locale locale) {
        try {
            return messageSource.getMessage(code, null, locale);
        } catch (Exception e) {
            return code;
        }
    }

}
