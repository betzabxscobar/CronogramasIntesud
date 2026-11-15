package ec.edu.intesud.crono.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "schedule_versions")
public class ScheduleVersion extends TimestampedEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "schedule_id", nullable = false)
  private Schedule schedule;

  /** Número de versión del horario (no es un bloqueo optimista). */
  @Column(name = "version", nullable = false)
  private int version;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "status", nullable = false)
  private ScheduleStatus status = ScheduleStatus.DRAFT;

  @Column(name = "reason")
  private String reason;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by_id", nullable = false)
  private User createdBy;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "modified_by_id", nullable = false)
  private User modifiedBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "published_by_id")
  private User publishedBy;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "unpublished_at")
  private Instant unpublishedAt;

  public Schedule getSchedule() {
    return schedule;
  }

  public void setSchedule(Schedule schedule) {
    this.schedule = schedule;
  }

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public ScheduleStatus getStatus() {
    return status;
  }

  public void setStatus(ScheduleStatus status) {
    this.status = status;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(User createdBy) {
    this.createdBy = createdBy;
  }

  public User getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(User modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public User getPublishedBy() {
    return publishedBy;
  }

  public void setPublishedBy(User publishedBy) {
    this.publishedBy = publishedBy;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(Instant publishedAt) {
    this.publishedAt = publishedAt;
  }

  public Instant getUnpublishedAt() {
    return unpublishedAt;
  }

  public void setUnpublishedAt(Instant unpublishedAt) {
    this.unpublishedAt = unpublishedAt;
  }
}
