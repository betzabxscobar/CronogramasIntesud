package ec.edu.intesud.crono.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Fechas en el mismo texto que enviaba la API de NestJS (Date de JavaScript en JSON), para que
 * el frontend reciba exactamente lo mismo: "2026-10-05T00:00:00.000Z".
 */
public final class ApiDates {

  private static final DateTimeFormatter INSTANT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

  private ApiDates() {}

  public static String instant(Instant value) {
    return value == null ? null : INSTANT.format(value);
  }

  /** Columna DATE: medianoche UTC de ese día. */
  public static String date(LocalDate value) {
    return value == null ? null : value + "T00:00:00.000Z";
  }

  /** Columna TIME: Prisma la devolvía como una hora del 1 de enero de 1970 en UTC. */
  public static String time(LocalTime value) {
    return value == null ? null : "1970-01-01T" + TIME.format(value) + ".000Z";
  }
}
