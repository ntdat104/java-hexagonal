package com.onemount.javahexagonal.domain.repo;
import com.onemount.javahexagonal.application.enums.OutboxStatus;
import com.onemount.javahexagonal.domain.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Date;
import java.util.List;
@Repository
public interface OutboxEventRepo extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findByStatusAndNextProcessAtBefore(OutboxStatus status, Date cutoff);
    List<OutboxEvent> findByAggregateId(String aggregateId);
}
