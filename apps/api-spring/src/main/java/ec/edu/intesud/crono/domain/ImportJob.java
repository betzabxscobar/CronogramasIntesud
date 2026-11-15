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
@Table(name = "import_jobs")
public class ImportJob extends TimestampedEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "career_id", nullable = false)
  private Career career;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "period_id")
  private AcademicPeriod period;

  @Column(name = "file_name", nullable = false)
  private String fileName;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "status", nullable = false)
  private ImportStatus status = ImportStatus.UPLOADED;

  @Column(name = "total_rows", nullable = false)
  private int totalRows;

  @Column(name = "valid_rows", nullable = false)
  private int validRows;

  @Column(name = "warning_rows", nullable = false)
  private int warningRows;

  @Column(name = "error_rows", nullable = false)
  private int errorRows;

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Career getCareer() {
    return career;
  }

  public void setCareer(Career career) {
    this.career = career;
  }

  public AcademicPeriod getPeriod() {
    return period;
  }

  public void setPeriod(AcademicPeriod period) {
    this.period = period;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public ImportStatus getStatus() {
    return status;
  }

  public void setStatus(ImportStatus status) {
    this.status = status;
  }

  public int getTotalRows() {
    return totalRows;
  }

  public void setTotalRows(int totalRows) {
    this.totalRows = totalRows;
  }

  public int getValidRows() {
    return validRows;
  }

  public void setValidRows(int validRows) {
    this.validRows = validRows;
  }

  public int getWarningRows() {
    return warningRows;
  }

  public void setWarningRows(int warningRows) {
    this.warningRows = warningRows;
  }

  public int getErrorRows() {
    return errorRows;
  }

  public void setErrorRows(int errorRows) {
    this.errorRows = errorRows;
  }
}
