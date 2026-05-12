package com.onemount.javahexagonal.domain.repo;
import com.onemount.javahexagonal.domain.model.SessionStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface SessionStepRepo extends JpaRepository<SessionStep, Long> {
    List<SessionStep> findBySessionIdOrderByStartedAtAsc(String sessionId);
    List<SessionStep> findBySessionIdAndNodeId(String sessionId, String nodeId);
}
