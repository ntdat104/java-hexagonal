package com.onemount.javahexagonal.domain.model;

import com.onemount.javahexagonal.application.enums.SessionChannel;
import com.onemount.javahexagonal.application.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import java.util.Date;

@Getter @Setter @Entity @Accessors(chain = true)
@Table(name = "sessions", indexes = {
    @Index(columnList = "tenant_id"),
    @Index(columnList = "workflow_uid"),
    @Index(columnList = "user_ref"),
    @Index(columnList = "status")
})
public class Session extends AbstractModel {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id; // UUID

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "workflow_definition_id")
    private Long workflowDefinitionId;

    @Column(name = "workflow_uid", nullable = false)
    private String workflowUid;

    @Column(name = "workflow_version")
    private Long workflowVersion;

    @Column(name = "user_ref")
    private String userRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel")
    private SessionChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status;

    @Column(name = "current_node_id")
    private String currentNodeId;

    // Accumulated SESSION_INPUT fields across all steps: {fieldName: value}
    @Column(name = "session_input_store", columnDefinition = "text")
    private String sessionInputStore;

    // Accumulated node outputs: {nodeId: {field: value}}
    @Column(name = "node_output_store", columnDefinition = "text")
    private String nodeOutputStore;

    @Column(name = "active_node_ids", columnDefinition = "text")
    private String activeNodeIds; // JSON: ["nodeId1", "nodeId2", ...]

    @Column(name = "started_at")
    private Date startedAt;

    @Column(name = "completed_at")
    private Date completedAt;

    @Column(name = "expires_at")
    private Date expiresAt;

    @Column(name = "metadata", columnDefinition = "text")
    private String metadata;
}
