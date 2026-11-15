package ec.edu.intesud.crono.imports;

import com.fasterxml.jackson.annotation.JsonInclude;
import ec.edu.intesud.crono.careers.CareerLookup.CareerOption;
import ec.edu.intesud.crono.domain.ImportRowStatus;
import ec.edu.intesud.crono.domain.ImportStatus;
import ec.edu.intesud.crono.imports.StartSheetParser.CatalogBlock;
import ec.edu.intesud.crono.imports.StartSheetParser.SkippedCell;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Respuestas del módulo de importación del libro de horarios. */
public final class ImportDtos {

  private ImportDtos() {}

  public record JobSummary(
      UUID id,
      String fileName,
      ImportStatus status,
      int totalRows,
      int validRows,
      int warningRows,
      int errorRows) {}

  public record Counts(int subjects, int professors, int spaces, int levels, int blocks) {}

  /** Resumen de las clases de un turno. */
  public record ShiftSummary(
      String shift,
      String sheetName,
      int blocks,
      Map<String, Integer> byLevel,
      int ignoredProfessorBlocks,
      int placeholders,
      List<String> notes,
      int missingModuleDates) {}

  public record BlockPreview(String periodName, List<ShiftSummary> shifts, int conflicts) {}

  /** Fila que la persona debe revisar antes de confirmar. */
  public record ReviewRow(
      String sheetName,
      int rowNumber,
      String entity,
      ImportRowStatus status,
      String label,
      List<String> messages) {}

  /** Vista previa de una importación. "blocks" solo va si se pidieron los horarios. */
  public record ImportPreview(
      JobSummary job,
      List<String> sheets,
      String activeCareerInBook,
      List<CatalogBlock> catalogBlocks,
      Counts counts,
      @JsonInclude(JsonInclude.Include.NON_NULL) BlockPreview blocks,
      List<ReviewRow> needsReview,
      List<SkippedCell> skipped,
      List<String> notes,
      boolean canCommit) {}

  public record Applied(int levels, int subjects, int professors, int spaces, int blocks) {}

  public record CommitResult(UUID jobId, String career, Applied applied) {}

  public record CancelResult(UUID id, ImportStatus status) {}

  public record JobListItem(
      UUID id,
      String fileName,
      ImportStatus status,
      int totalRows,
      int validRows,
      int warningRows,
      int errorRows,
      String createdAt,
      CareerOption career) {}

  public record JobRow(
      UUID id,
      UUID importJobId,
      String sheetName,
      int rowNumber,
      ImportRowStatus status,
      Object rawData,
      Object messages) {}

  public record JobDetail(
      UUID id,
      UUID userId,
      UUID careerId,
      UUID periodId,
      String fileName,
      ImportStatus status,
      int totalRows,
      int validRows,
      int warningRows,
      int errorRows,
      String createdAt,
      String updatedAt,
      List<JobRow> rows) {}
}
