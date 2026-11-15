package ec.edu.intesud.crono.repository;

import ec.edu.intesud.crono.domain.ScheduleVersion;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleVersionRepository extends JpaRepository<ScheduleVersion, UUID> {}
