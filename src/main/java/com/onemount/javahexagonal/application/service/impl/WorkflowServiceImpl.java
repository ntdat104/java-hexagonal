package com.onemount.javahexagonal.application.service.impl;

import com.onemount.javahexagonal.application.service.WorkflowService;
import com.onemount.javahexagonal.domain.model.WorkflowDefinition;
import com.onemount.javahexagonal.domain.repo.WorkflowDefinitionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowDefinitionRepo workflowDefinitionRepo;

    @Override
    public WorkflowDefinition create(WorkflowDefinition workflowDefinition) {
        return workflowDefinitionRepo.save(workflowDefinition);
    }
}
