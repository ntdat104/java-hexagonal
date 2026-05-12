package com.onemount.javahexagonal.domain.model;

import com.onemount.javahexagonal.application.enums.WebhookDeliveryStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import java.util.Date;

// Tracks each webhook delivery attempt to the customer's endpoint.
// Retry logic is independent of Kafka — handles HTTP-level failures per tenant URL.
@Getter @Setter @Entity @Accessors(chain = true)
@Table(name = "webhook_deliveries", indexes = {
    @Index(columnList = "session_id"),
    @Index(columnList = "tenant_id, status"),
    @Index(columnList = "status, next_retry_at")
})
public class WebhookDelivery {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "request_body", columnDefinition = "text")
    private String requestBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "attempt_number")
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WebhookDeliveryStatus status;

    @Column(name = "sent_at")
    private Date sentAt;

    @Column(name = "next_retry_at")
    private Date nextRetryAt;

    @Column(name = "created_at", nullable = false)
    private Date createdAt;
}
