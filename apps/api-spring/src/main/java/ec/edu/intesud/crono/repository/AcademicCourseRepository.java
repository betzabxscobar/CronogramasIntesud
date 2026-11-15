package ec.edu.intesud.crono.repository;

import ec.edu.intesud.crono.domain.AcademicCourse;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicCourseRepository extends JpaRepository<AcademicCourse, UUID> {}
