package com.onemount.javahexagonal.application.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.onemount.javahexagonal.application.enums.SessionChannel;
import com.onemount.javahexagonal.infrastructure.anotation.EnumValid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import java.util.Map;

@Getter @Setter @Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateSessionRequest extends BaseRequest {

    @NotNull(message = "400011")
    private Long tenantId;

    @NotBlank(message = "400027")
    private String workflowUid;

    @NotBlank(message = "400028")
    private String userRef;

    @NotNull(message = "400029")
    @EnumValid(enumClass = SessionChannel.class, message = "400030")
    private String channel;

    // Initial SESSION_INPUT fields — engine will execute nodes that can be resolved immediately
    private Map<String, Object> inputData;

    private Map<String, Object> metadata;
}
