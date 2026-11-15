package ec.edu.intesud.crono.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "file_assets")
public class FileAsset extends CreatedEntity {
  @Column(name = "storage_key", nullable = false, unique = true)
  private String storageKey;

  @Column(name = "original_name", nullable = false)
  private String originalName;

  @Column(name = "mime_type", nullable = false)
  private String mimeType;

  @Column(name = "size_bytes", nullable = false)
  private int sizeBytes;

  public String getStorageKey() {
    return storageKey;
  }

  public void setStorageKey(String storageKey) {
    this.storageKey = storageKey;
  }

  public String getOriginalName() {
    return originalName;
  }

  public void setOriginalName(String originalName) {
    this.originalName = originalName;
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public int getSizeBytes() {
    return sizeBytes;
  }

  public void setSizeBytes(int sizeBytes) {
    this.sizeBytes = sizeBytes;
  }
}
