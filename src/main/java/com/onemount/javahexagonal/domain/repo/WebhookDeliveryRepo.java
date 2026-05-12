package com.onemount.javahexagonal.domain.repo;
import com.onemount.javahexagonal.application.enums.WebhookDeliveryStatus;
import com.onemount.javahexagonal.domain.model.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Date;
import java.util.List;
@Repository
public interface WebhookDeliveryRepo extends JpaRepository<WebhookDelivery, Long> {
    List<WebhookDelivery> findBySessionId(String sessionId);
    List<WebhookDelivery> findByStatusAndNextRetryAtBefore(WebhookDeliveryStatus status, Date cutoff);
    List<WebhookDelivery> findByTenantIdAndStatus(Long tenantId, WebhookDeliveryStatus status);
}
