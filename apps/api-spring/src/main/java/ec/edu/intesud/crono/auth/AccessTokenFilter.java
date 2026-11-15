package ec.edu.intesud.crono.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lee el token "Authorization: Bearer ..." y, si es válido y el usuario sigue activo, lo deja
 * como usuario autenticado de la petición. Equivale al AccessTokenGuard de NestJS.
 *
 * <p>Un token inválido no corta la petición aquí: las rutas públicas deben seguir funcionando
 * (por ejemplo, cerrar sesión con un token ya vencido). Si la ruta exige autenticación, Spring
 * Security responde 401 después.
 */
public class AccessTokenFilter extends OncePerRequestFilter {

  public static final String ERROR_ATTRIBUTE = "crono.accessTokenError";

  private final JwtDecoder jwtDecoder;
  private final AuthenticatedUserLoader userLoader;

  public AccessTokenFilter(JwtDecoder jwtDecoder, AuthenticatedUserLoader userLoader) {
    this.jwtDecoder = jwtDecoder;
    this.userLoader = userLoader;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      try {
        authenticate(header.substring("Bearer ".length()));
      } catch (RuntimeException exception) {
        SecurityContextHolder.clearContext();
        request.setAttribute(ERROR_ATTRIBUTE, "Sesión inválida o vencida");
      }
    }
    chain.doFilter(request, response);
  }

  private void authenticate(String token) {
    Jwt jwt = jwtDecoder.decode(token);
    if (!"access".equals(jwt.getClaimAsString("type"))) {
      throw new IllegalArgumentException("Tipo de token inválido");
    }
    AuthenticatedUser user =
        userLoader
            .loadActive(UUID.fromString(jwt.getSubject()))
            .orElseThrow(() -> new IllegalArgumentException("Usuario inactivo"));

    // El rol queda como autoridad ROLE_ADMIN / ROLE_COORDINATOR para usar hasRole(...).
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
  }
}
