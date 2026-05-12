package com.onemount.javahexagonal.domain.repo;

import com.onemount.javahexagonal.domain.model.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkflowDefinitionRepo extends JpaRepository<WorkflowDefinition, Long> {
    boolean existsByUid(String uid);
    Optional<WorkflowDefinition> findByUid(String uid);
}
