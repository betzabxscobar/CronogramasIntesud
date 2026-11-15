package ec.edu.intesud.crono.domain;

/** Corresponde al tipo enum "ImportStatus" de PostgreSQL. */
public enum ImportStatus {
  UPLOADED,
  VALIDATING,
  INVALID,
  READY,
  IMPORTED,
  CANCELLED
}
