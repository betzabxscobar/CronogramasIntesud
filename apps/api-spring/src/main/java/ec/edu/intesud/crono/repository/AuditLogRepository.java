package ec.edu.intesud.crono.repository;

import ec.edu.intesud.crono.domain.AuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {}
