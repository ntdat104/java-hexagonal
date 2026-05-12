package com.onemount.javahexagonal.domain.repo;
import com.onemount.javahexagonal.application.enums.SessionStatus;
import com.onemount.javahexagonal.domain.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface SessionRepo extends JpaRepository<Session, String> {
    List<Session> findByTenantIdAndStatus(Long tenantId, SessionStatus status);
    List<Session> findByTenantIdAndWorkflowUid(Long tenantId, String workflowUid);
}
