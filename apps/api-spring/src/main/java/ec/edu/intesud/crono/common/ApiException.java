package ec.edu.intesud.crono.common;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Error de negocio que se devuelve al cliente con un código HTTP y un mensaje. */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final List<String> messages;

  public ApiException(HttpStatus status, String message) {
    super(message);
    this.status = status;
    this.messages = null;
  }

  /** Error con varios mensajes: "message" se devuelve como lista. */
  public ApiException(HttpStatus status, List<String> messages) {
    super(String.join("; ", messages));
    this.status = status;
    this.messages = List.copyOf(messages);
  }

  /** El texto del error, o la lista de textos si hay varios. */
  public Object getBody() {
    return messages == null ? getMessage() : messages;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public static ApiException badRequest(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, message);
  }

  public static ApiException unauthorized(String message) {
    return new ApiException(HttpStatus.UNAUTHORIZED, message);
  }

  public static ApiException forbidden(String message) {
    return new ApiException(HttpStatus.FORBIDDEN, message);
  }

  public static ApiException notFound(String message) {
    return new ApiException(HttpStatus.NOT_FOUND, message);
  }

  public static ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, message);
  }
}
