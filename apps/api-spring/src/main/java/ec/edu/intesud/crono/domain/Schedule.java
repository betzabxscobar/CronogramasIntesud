package ec.edu.intesud.crono.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "schedules")
public class Schedule extends TimestampedEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_id", nullable = false)
  private AcademicCourse course;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "shift", nullable = false)
  private Shift shift;

  public AcademicCourse getCourse() {
    return course;
  }

  public void setCourse(AcademicCourse course) {
    this.course = course;
  }

  public Shift getShift() {
    return shift;
  }

  public void setShift(Shift shift) {
    this.shift = shift;
  }
}
