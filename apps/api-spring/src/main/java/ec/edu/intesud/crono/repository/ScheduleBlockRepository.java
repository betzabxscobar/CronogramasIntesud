package ec.edu.intesud.crono.repository;

import ec.edu.intesud.crono.domain.ScheduleBlock;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleBlockRepository extends JpaRepository<ScheduleBlock, UUID> {}
