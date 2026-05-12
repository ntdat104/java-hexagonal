package com.onemount.javahexagonal.domain.repo;
import com.onemount.javahexagonal.application.enums.EventType;
import com.onemount.javahexagonal.domain.model.SessionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface SessionEventRepo extends JpaRepository<SessionEvent, Long> {
    List<SessionEvent> findBySessionIdOrderByOccurredAtAsc(String sessionId);
    List<SessionEvent> findByTenantIdAndEventType(Long tenantId, EventType eventType);
}
