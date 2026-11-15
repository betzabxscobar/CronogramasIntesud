package ec.edu.intesud.crono.repository;

import ec.edu.intesud.crono.domain.Subject;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {}
