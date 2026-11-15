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
@Table(name = "academic_courses")
public class AcademicCourse extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "period_id", nullable = false)
  private AcademicPeriod period;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "level_id", nullable = false)
  private Level level;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "student_count")
  private Integer studentCount;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "status", nullable = false)
  private RecordStatus status = RecordStatus.ACTIVE;

  public AcademicPeriod getPeriod() {
    return period;
  }

  public void setPeriod(AcademicPeriod period) {
    this.period = period;
  }

  public Level getLevel() {
    return level;
  }

  public void setLevel(Level level) {
    this.level = level;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getStudentCount() {
    return studentCount;
  }

  public void setStudentCount(Integer studentCount) {
    this.studentCount = studentCount;
  }

  public RecordStatus getStatus() {
    return status;
  }

  public void setStatus(RecordStatus status) {
    this.status = status;
  }
}
