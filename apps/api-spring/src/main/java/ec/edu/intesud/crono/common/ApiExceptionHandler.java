package ec.edu.intesud.crono.common;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Convierte todas las excepciones al formato de error que espera el frontend. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  public static final String FORBIDDEN_MESSAGE = "No tiene permisos para esta operación";

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiError> handleApiException(ApiException exception) {
    return ResponseEntity.status(exception.getStatus())
        .body(ApiError.of(exception.getStatus(), exception.getBody()));
  }

  /** Acceso denegado por Spring Security dentro de un controlador. */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiError.of(HttpStatus.FORBIDDEN, FORBIDDEN_MESSAGE));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
    log.error("Error no controlado", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"));
  }

  /** Errores de validación de los DTO: "message" es la lista de problemas por campo. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<String> messages =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .sorted()
            .toList();
    return ResponseEntity.badRequest().body(ApiError.of(HttpStatus.BAD_REQUEST, messages));
  }

  /** Resto de errores estándar de Spring MVC (JSON mal formado, método no permitido...). */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    if (status.is5xxServerError()) {
      log.error("Error no controlado", exception);
    }
    return ResponseEntity.status(status).headers(headers).body(ApiError.of(status, null));
  }
}
