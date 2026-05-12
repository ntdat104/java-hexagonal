package com.onemount.javahexagonal.domain.model;

import com.onemount.javahexagonal.application.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import java.util.Date;

// Transactional outbox — written in the same transaction as the session update.
// A relay worker reads PENDING rows and publishes to Kafka, then marks PUBLISHED.
// Prevents event loss when the service crashes between DB write and Kafka publish.
@Getter @Setter @Entity @Accessors(chain = true)
@Table(name = "outbox_events", indexes = {
    @Index(columnList = "status, next_process_at"),
    @Index(columnList = "aggregate_id")
})
public class OutboxEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId; // session_id

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType; // "SESSION"

    @Column(name = "payload", columnDefinition = "text", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;

    @Column(name = "attempts")
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    @Column(name = "processed_at")
    private Date processedAt;

    @Column(name = "next_process_at")
    private Date nextProcessAt;
}
