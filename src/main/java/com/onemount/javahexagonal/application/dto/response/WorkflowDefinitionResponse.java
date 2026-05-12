package com.onemount.javahexagonal.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.onemount.javahexagonal.application.dto.request.CreateWorkflowRequest.*;
import com.onemount.javahexagonal.application.enums.WorkflowStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter 
@Setter 
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class WorkflowDefinitionResponse {
    private Long id;
    private String uid;
    private Long tenantId;
    private String name;
    private WorkflowStatus status;
    private Long timeout;
    private Long version;
    
    // Sử dụng lại chính cấu trúc DTO từ Request để đảm bảo tính nhất quán
    private DefinitionDto definition;

    public static WorkflowDefinitionResponse of(
            com.onemount.javahexagonal.domain.model.WorkflowDefinition entity,
            DefinitionDto parsedDefinition) {
        return WorkflowDefinitionResponse.builder()
                .id(entity.getId())
                .uid(entity.getUid())
                .tenantId(entity.getTenantId())
                .name(entity.getName())
                .status(entity.getStatus())
                .timeout(entity.getTimeout())
                .version(entity.getVersion())
                .definition(parsedDefinition)
                .build();
    }
}