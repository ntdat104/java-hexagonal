package com.onemount.javahexagonal.domain.model;

import com.onemount.javahexagonal.application.enums.SessionOutcome;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import java.util.Date;

// Denormalized final state — 1 row per completed session.
// Enables fast queries without JOINs across session_steps.
@Getter @Setter @Entity @Accessors(chain = true)
@Table(name = "session_snapshots", indexes = {
    @Index(columnList = "tenant_id"),
    @Index(columnList = "workflow_uid"),
    @Index(columnList = "outcome")
})
public class SessionSnapshot extends AbstractModel {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "workflow_uid")
    private String workflowUid;

    @Column(name = "workflow_version")
    private Long workflowVersion;

    @Column(name = "user_ref")
    private String userRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private SessionOutcome outcome;

    // Aggregated final outputs from all nodes – the complete KYC result bundle
    @Column(name = "decision_data", columnDefinition = "text")
    private String decisionData;

    @Column(name = "final_node_id")
    private String finalNodeId;

    @Column(name = "total_steps")
    private int totalSteps;

    @Column(name = "completed_at")
    private Date completedAt;
}
