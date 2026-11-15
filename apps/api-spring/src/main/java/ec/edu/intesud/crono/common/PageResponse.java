package ec.edu.intesud.crono.common;

import java.util.List;

/** Respuesta paginada: {"items": [...], "pagination": {...}}. */
public record PageResponse<T>(List<T> items, Pagination pagination) {

  public record Pagination(int page, int pageSize, long total, int totalPages) {}

  public static <T> PageResponse<T> of(List<T> items, int page, int pageSize, long total) {
    int totalPages = (int) Math.max(1, (total + pageSize - 1) / pageSize);
    return new PageResponse<>(items, new Pagination(page, pageSize, total, totalPages));
  }
}
