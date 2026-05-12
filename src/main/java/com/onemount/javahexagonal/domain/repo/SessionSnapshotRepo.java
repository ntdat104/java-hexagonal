package com.onemount.javahexagonal.domain.repo;
import com.onemount.javahexagonal.application.enums.SessionOutcome;
import com.onemount.javahexagonal.domain.model.SessionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
@Repository
public interface SessionSnapshotRepo extends JpaRepository<SessionSnapshot, Long> {
    Optional<SessionSnapshot> findBySessionId(String sessionId);
    List<SessionSnapshot> findByTenantIdAndOutcome(Long tenantId, SessionOutcome outcome);
}
