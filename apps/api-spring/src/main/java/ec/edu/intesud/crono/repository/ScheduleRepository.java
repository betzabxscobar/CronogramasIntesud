package ec.edu.intesud.crono.repository;

import ec.edu.intesud.crono.domain.Schedule;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {}
