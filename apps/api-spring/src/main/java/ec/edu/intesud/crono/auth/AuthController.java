package ec.edu.intesud.crono.auth;

import ec.edu.intesud.crono.auth.AuthService.AuthResult;
import ec.edu.intesud.crono.auth.AuthService.SessionMetadata;
import ec.edu.intesud.crono.auth.dto.AuthResponse;
import ec.edu.intesud.crono.auth.dto.RequestCodeRequest;
import ec.edu.intesud.crono.auth.dto.VerifyCodeRequest;
import ec.edu.intesud.crono.config.CronoProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private static final String REFRESH_COOKIE = "crono_refresh";
  private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

  private final AuthService auth;
  private final CronoProperties properties;

  public AuthController(AuthService auth, CronoProperties properties) {
    this.auth = auth;
    this.properties = properties;
  }

  /** Solicita un código temporal de acceso por correo. Ruta pública. */
  @PostMapping("/request-code")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, String> requestCode(
      @Valid @RequestBody RequestCodeRequest input, HttpServletRequest request) {
    return Map.of("message", auth.requestCode(input.nationalId(), metadata(request)));
  }

  /** Verifica el código e inicia una sesión. Ruta pública. */
  @PostMapping("/verify-code")
  public AuthResponse verifyCode(
      @Valid @RequestBody VerifyCodeRequest input,
      HttpServletRequest request,
      HttpServletResponse response) {
    AuthResult result = auth.verifyCode(input.nationalId(), input.code(), metadata(request));
    setRefreshCookie(response, result.refreshToken(), Duration.ofHours(8));
    return new AuthResponse(result.accessToken(), result.user());
  }

  /** Renueva una sesión activa a partir de la cookie de refresco. Ruta pública. */
  @PostMapping("/refresh")
  public AuthResponse refresh(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
      HttpServletResponse response) {
    AuthResult result = auth.refresh(refreshToken);
    setRefreshCookie(response, result.refreshToken(), Duration.ofHours(8));
    return new AuthResponse(result.accessToken(), result.user());
  }

  /** Cierra y revoca la sesión actual. Ruta pública. */
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
      HttpServletResponse response) {
    auth.logout(refreshToken);
    setRefreshCookie(response, "", Duration.ZERO);
  }

  /** Obtiene el usuario autenticado. */
  @GetMapping("/me")
  public AuthenticatedUser me(@AuthenticationPrincipal AuthenticatedUser user) {
    return user;
  }

  private SessionMetadata metadata(HttpServletRequest request) {
    return new SessionMetadata(request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
  }

  private void setRefreshCookie(HttpServletResponse response, String token, Duration maxAge) {
    ResponseCookie cookie =
        ResponseCookie.from(REFRESH_COOKIE, token)
            .httpOnly(true)
            .secure(properties.production())
            .sameSite("Lax")
            .path(REFRESH_COOKIE_PATH)
            .maxAge(maxAge)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }
}
