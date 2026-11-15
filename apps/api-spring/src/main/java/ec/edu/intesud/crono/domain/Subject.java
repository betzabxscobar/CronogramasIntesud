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
@Table(name = "subjects")
public class Subject extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "career_id", nullable = false)
  private Career career;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "level_id")
  private Level level;

  @Column(name = "code")
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "weekly_hours")
  private Integer weeklyHours;

  @Column(name = "requires_individual_computer", nullable = false)
  private boolean requiresIndividualComputer;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "status", nullable = false)
  private RecordStatus status = RecordStatus.ACTIVE;

  public Career getCareer() {
    return career;
  }

  public void setCareer(Career career) {
    this.career = career;
  }

  public Level getLevel() {
    return level;
  }

  public void setLevel(Level level) {
    this.level = level;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getWeeklyHours() {
    return weeklyHours;
  }

  public void setWeeklyHours(Integer weeklyHours) {
    this.weeklyHours = weeklyHours;
  }

  public boolean isRequiresIndividualComputer() {
    return requiresIndividualComputer;
  }

  public void setRequiresIndividualComputer(boolean requiresIndividualComputer) {
    this.requiresIndividualComputer = requiresIndividualComputer;
  }

  public RecordStatus getStatus() {
    return status;
  }

  public void setStatus(RecordStatus status) {
    this.status = status;
  }
}
