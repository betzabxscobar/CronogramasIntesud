package ec.edu.intesud.crono.repository;

import ec.edu.intesud.crono.domain.Level;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LevelRepository extends JpaRepository<Level, UUID> {}
