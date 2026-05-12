package com.onemount.javahexagonal.domain.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.onemount.javahexagonal.application.enums.SessionStepStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import java.util.Date;

@Getter @Setter @Entity @Accessors(chain = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Table(name = "session_steps", indexes = {
    @Index(columnList = "session_id"),
    @Index(columnList = "session_id, node_id")
})
public class SessionStep extends AbstractModel {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private String sessionId;

    @Column(name = "node_id", nullable = false, updatable = false)
    private String nodeId;

    @Column(name = "node_type", updatable = false)
    private String nodeType;

    @Column(name = "attempt_number")
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStepStatus status;

    // Snapshot of actual resolved inputs used during execution (for audit/replay)
    @Column(name = "input_snapshot", columnDefinition = "text", updatable = false)
    private String inputSnapshot;

    // Serialized output JSON from the node processor
    @Column(name = "output", columnDefinition = "text")
    private String output;

    @Column(name = "started_at")
    private Date startedAt;

    @Column(name = "completed_at")
    private Date completedAt;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
}
