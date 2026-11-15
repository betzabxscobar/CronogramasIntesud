package ec.edu.intesud.crono.exports;

import ec.edu.intesud.crono.auth.Roles;
import ec.edu.intesud.crono.common.ApiException;
import ec.edu.intesud.crono.domain.UserRole;
import ec.edu.intesud.crono.exports.ExportsService.ExportedWorkbook;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Descargas en Excel. */
@RestController
@RequestMapping("/exports")
@Roles({UserRole.ADMIN, UserRole.COORDINATOR})
public class ExportsController {

  private static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final ExportsService exports;

  public ExportsController(ExportsService exports) {
    this.exports = exports;
  }

  /**
   * Libro de horarios en .xlsx con las hojas Inicio, horario diurno y horario nocturno. Solo
   * incluye horarios publicados.
   */
  @GetMapping("/horarios")
  public ResponseEntity<byte[]> horarios(
      @RequestParam(required = false) UUID periodId,
      @RequestParam(required = false) UUID careerId) {
    if (periodId == null || careerId == null) {
      throw ApiException.badRequest("Indica el periodo y la carrera que quieres exportar.");
    }
    ExportedWorkbook workbook = exports.buildWorkbook(periodId, careerId);
    String fileName = workbook.fileName();
    // El frontend lee filename="..."; filename* lleva el nombre completo en UTF-8.
    String plain = fileName.replace("\"", "").replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", "_");
    String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + plain + "\"; filename*=UTF-8''" + encoded)
        .contentType(XLSX)
        .contentLength(workbook.content().length)
        .body(workbook.content());
  }
}
