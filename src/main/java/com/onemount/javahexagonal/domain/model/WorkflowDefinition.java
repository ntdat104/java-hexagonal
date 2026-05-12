package com.onemount.javahexagonal.domain.model;

import com.onemount.javahexagonal.application.enums.WorkflowStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "workflow_definitions")
public class WorkflowDefinition extends AbstractModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "status", nullable = false)
    private WorkflowStatus status;

    @Column(name = "timout", nullable = false)
    private Long timeout;

    @Column(name = "definition", columnDefinition = "text", nullable = false)
    private String definition;

    @Column(name = "version", nullable = false)
    private Long version;
}
