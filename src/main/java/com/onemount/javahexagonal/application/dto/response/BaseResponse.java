package com.onemount.javahexagonal.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BaseResponse<T> {

    private Meta meta;

    private T data;

    public static <T> BaseResponse<T> ofSucceeded(T data) {
        return new BaseResponse<T>()
                .setData(data)
                .setMeta(Meta.createSuccess());
    }

    public static <T> BaseResponse<T> ofSucceeded() {
        return new BaseResponse<T>()
                .setMeta(Meta.createSuccess());
    }

}
