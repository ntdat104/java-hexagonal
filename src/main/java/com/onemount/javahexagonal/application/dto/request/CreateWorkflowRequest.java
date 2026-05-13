package com.onemount.javahexagonal.application.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.onemount.javahexagonal.application.enums.WorkflowStatus;
import com.onemount.javahexagonal.infrastructure.anotation.EnumValid;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Getter @Setter @Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateWorkflowRequest extends BaseRequest {

    @NotNull(message = "400011")
    private Long tenantId;

    @NotBlank(message = "400012")
    @Size(max = 255, message = "400013")
    private String name;

    @NotNull(message = "400014")
    @Min(value = 1, message = "400015")
    private Long version;

    private Long timeout;

    @NotNull(message = "400017")
    @Valid
    private DefinitionDto definition;

    // ─── Nested DTOs ─────────────────────────────────────────────────────────

    @Getter @Setter
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DefinitionDto {

        @NotBlank(message = "400020")
        private String startNode;

        @NotEmpty(message = "400021")
        @Valid
        private List<NodeDto> nodes;

        @NotNull(message = "400022")
        private List<EdgeDto> edges;
    }

    @Getter @Setter
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NodeDto {

        @NotBlank(message = "400023")
        private String id;

        @NotBlank(message = "400024")
        private String type;

        private String executionMode;
        private Map<String, Object> config;
        private Map<String, Object> retryPolicy;
        private Map<String, Object> inputSchema;
        private Map<String, Object> outputSchema;
        private Map<String, InputMapEntryDto> inputMap;
        private List<FallbackCaseDto> fallbackChain;
    }

    @Getter @Setter
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EdgeDto {

        @NotBlank(message = "400025")
        private String from;

        @NotBlank(message = "400026")
        private String to;

        // Flat condition: {"field": "outcome", "op": "EQ", "value": "APPROVE"}
        // null = unconditional
        private Map<String, Object> condition;
    }

    @Getter @Setter
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InputMapEntryDto {
        private String type;      // SESSION_INPUT | NODE_OUTPUT | TENANT_CONFIG | CONFIG_VALUE
        private String key;       // SESSION_INPUT: session key; TENANT_CONFIG/CONFIG_VALUE: literal key
        private String fieldType; // SESSION_INPUT: file | text | etc (optional hint)
        private String nodeId;    // NODE_OUTPUT: source node id
        private String field;     // NODE_OUTPUT: dot-path field (e.g. "fields.idNumber")
    }

    @Getter @Setter
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FallbackCaseDto {
        private String name;
        private List<String> requiredInputs;
        private String reasonCode;
    }
}
