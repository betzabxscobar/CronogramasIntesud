package ec.edu.intesud.crono.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "professor_careers")
public class ProfessorCareer {
  @EmbeddedId
  private ProfessorCareerId id;

  @MapsId("professorId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "professor_id", nullable = false)
  private Professor professor;

  @MapsId("careerId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "career_id", nullable = false)
  private Career career;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "status", nullable = false)
  private RecordStatus status = RecordStatus.ACTIVE;

  @CreationTimestamp
  @Column(name = "linked_at", nullable = false, updatable = false)
  private Instant linkedAt;

  public ProfessorCareerId getId() {
    return id;
  }

  public void setId(ProfessorCareerId id) {
    this.id = id;
  }

  public Professor getProfessor() {
    return professor;
  }

  public void setProfessor(Professor professor) {
    this.professor = professor;
  }

  public Career getCareer() {
    return career;
  }

  public void setCareer(Career career) {
    this.career = career;
  }

  public RecordStatus getStatus() {
    return status;
  }

  public void setStatus(RecordStatus status) {
    this.status = status;
  }

  public Instant getLinkedAt() {
    return linkedAt;
  }

  public void setLinkedAt(Instant linkedAt) {
    this.linkedAt = linkedAt;
  }
}
