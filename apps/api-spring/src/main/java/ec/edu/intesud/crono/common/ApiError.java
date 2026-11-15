package ec.edu.intesud.crono.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Cuerpo de error con el mismo formato que devolvía NestJS, para que el frontend no cambie:
 * {"message": "...", "error": "Unauthorized", "statusCode": 401}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(Object message, String error, int statusCode) {

  public static ApiError of(HttpStatusCode status, Object message) {
    HttpStatus known = HttpStatus.resolve(status.value());
    String reason = known == null ? null : known.getReasonPhrase();
    if (status.is5xxServerError()) {
      return new ApiError(message, null, status.value());
    }
    return new ApiError(message == null ? reason : message, reason, status.value());
  }
}
