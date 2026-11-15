package ec.edu.intesud.crono.config;

import ec.edu.intesud.crono.common.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Límite de peticiones por IP y ruta en ventanas de un minuto (equivale a @nestjs/throttler):
 * 120 por minuto en general y 5 por minuto al pedir o verificar el código de acceso.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final long WINDOW_MS = 60_000;
  private static final int DEFAULT_LIMIT = 120;
  private static final int LOGIN_LIMIT = 5;
  private static final Set<String> LOGIN_ROUTES =
      Set.of("POST /auth/request-code", "POST /auth/verify-code");
  private static final Pattern IDENTIFIER =
      Pattern.compile("/(?:[0-9a-fA-F]{8}-[0-9a-fA-F-]{27}|\\d+)(?=/|$)");

  private final ApiErrorWriter errorWriter;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();
  private volatile long lastCleanup = System.currentTimeMillis();

  public RateLimitFilter(ApiErrorWriter errorWriter) {
    this.errorWriter = errorWriter;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long now = System.currentTimeMillis();
    String path = request.getRequestURI().substring(request.getContextPath().length());
    // Las rutas con identificador comparten contador: /careers/<uuid> -> /careers/:id
    String route = request.getMethod() + " " + IDENTIFIER.matcher(path).replaceAll("/:id");
    int limit = LOGIN_ROUTES.contains(route) ? LOGIN_LIMIT : DEFAULT_LIMIT;

    Window window =
        windows.compute(
            request.getRemoteAddr() + " " + route,
            (key, current) ->
                current == null || now - current.startedAt >= WINDOW_MS
                    ? new Window(now, 1)
                    : new Window(current.startedAt, current.count + 1));
    cleanUp(now);

    if (window.count > limit) {
      errorWriter.write(
          response, HttpStatus.TOO_MANY_REQUESTS, "ThrottlerException: Too Many Requests");
      return;
    }
    chain.doFilter(request, response);
  }

  private void cleanUp(long now) {
    if (now - lastCleanup < WINDOW_MS) return;
    lastCleanup = now;
    windows.values().removeIf(window -> now - window.startedAt >= WINDOW_MS);
  }

  private record Window(long startedAt, int count) {}
}
