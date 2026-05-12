package com.onemount.javahexagonal.domain.model;

import com.onemount.javahexagonal.application.enums.EventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import java.util.Date;

// Append-only fine-grained event log — records every state transition.
// No UPDATE or DELETE. Used for debug, compliance, and session replay.
@Getter @Setter @Entity @Accessors(chain = true)
@Table(name = "session_events", indexes = {
    @Index(columnList = "session_id"),
    @Index(columnList = "tenant_id, event_type"),
    @Index(columnList = "occurred_at")
})
public class SessionEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private String sessionId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false)
    private EventType eventType;

    @Column(name = "node_id", updatable = false)
    private String nodeId;

    // SYSTEM or USER
    @Column(name = "actor_type", updatable = false)
    private String actorType;

    @Column(name = "payload", columnDefinition = "text", updatable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Date occurredAt;
}
