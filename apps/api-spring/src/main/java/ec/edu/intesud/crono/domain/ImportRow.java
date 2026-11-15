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
@Table(name = "import_rows")
public class ImportRow extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "import_job_id", nullable = false)
  private ImportJob importJob;

  @Column(name = "sheet_name", nullable = false)
  private String sheetName;

  @Column(name = "row_number", nullable = false)
  private int rowNumber;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "status", nullable = false)
  private ImportRowStatus status;

  /** JSON en texto; la columna es jsonb. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_data", nullable = false)
  private String rawData;

  /** JSON en texto; la columna es jsonb. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "messages", nullable = false)
  private String messages;

  public ImportJob getImportJob() {
    return importJob;
  }

  public void setImportJob(ImportJob importJob) {
    this.importJob = importJob;
  }

  public String getSheetName() {
    return sheetName;
  }

  public void setSheetName(String sheetName) {
    this.sheetName = sheetName;
  }

  public int getRowNumber() {
    return rowNumber;
  }

  public void setRowNumber(int rowNumber) {
    this.rowNumber = rowNumber;
  }

  public ImportRowStatus getStatus() {
    return status;
  }

  public void setStatus(ImportRowStatus status) {
    this.status = status;
  }

  public String getRawData() {
    return rawData;
  }

  public void setRawData(String rawData) {
    this.rawData = rawData;
  }

  public String getMessages() {
    return messages;
  }

  public void setMessages(String messages) {
    this.messages = messages;
  }
}
