package com.onemount.javahexagonal.application.service;

import com.onemount.javahexagonal.domain.model.WorkflowDefinition;

public interface WorkflowService {
    WorkflowDefinition create(WorkflowDefinition workflowDefinition);
}
