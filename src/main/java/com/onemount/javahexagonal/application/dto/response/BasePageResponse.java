package com.onemount.javahexagonal.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BasePageResponse<T> {

    private Meta meta;

    private List<T> data;

    public static <T> BasePageResponse<T> ofSucceeded(Page<T> data) {
        return new BasePageResponse<T>()
                .setData(data.getContent())
                .setMeta(Meta.createSuccess(data.getTotalElements()));
    }
}
