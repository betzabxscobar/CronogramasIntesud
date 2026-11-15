package ec.edu.intesud.crono.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import ec.edu.intesud.crono.auth.AccessTokenFilter;
import ec.edu.intesud.crono.auth.AuthenticatedUserLoader;
import ec.edu.intesud.crono.common.ApiErrorWriter;
import ec.edu.intesud.crono.common.ApiExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Seguridad de la API. Equivale a los guards globales de NestJS:
 *
 * <ul>
 *   <li>Todo requiere un token de acceso, salvo las rutas públicas (antes {@code @Public()}).
 *   <li>Los roles se exigen en cada controlador con {@code @PreAuthorize("hasRole('ADMIN')")}
 *       (antes {@code @Roles(UserRole.ADMIN)}).
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  /** Rutas que no exigen token. Son relativas al context-path /api/v1. */
  private static final String[] PUBLIC_ROUTES = {
    "/health",
    "/auth/request-code",
    "/auth/verify-code",
    "/auth/refresh",
    "/auth/logout",
    "/public/**",
    "/error"
  };

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtDecoder jwtDecoder,
      AuthenticatedUserLoader userLoader,
      ApiErrorWriter errorWriter)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests.requestMatchers(PUBLIC_ROUTES).permitAll().anyRequest().authenticated())
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, exception) -> {
                          Object tokenError =
                              request.getAttribute(AccessTokenFilter.ERROR_ATTRIBUTE);
                          errorWriter.write(
                              response,
                              HttpStatus.UNAUTHORIZED,
                              tokenError == null ? "Acceso no autorizado" : tokenError.toString());
                        })
                    .accessDeniedHandler(
                        (request, response, exception) ->
                            errorWriter.write(
                                response,
                                HttpStatus.FORBIDDEN,
                                ApiExceptionHandler.FORBIDDEN_MESSAGE)))
        .addFilterBefore(
            new RateLimitFilter(errorWriter), UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(
            new AccessTokenFilter(jwtDecoder, userLoader),
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(CronoProperties properties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(properties.webOrigin()));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  /**
   * Clave con la que se firman los tokens: SESSION_SECRET como texto UTF-8 y HS256, igual que
   * hacía @nestjs/jwt, de modo que los tokens de una API sirven en la otra.
   */
  @Bean
  public SecretKey jwtSecretKey(CronoProperties properties) {
    String secret = properties.sessionSecret();
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException("SESSION_SECRET debe tener al menos 32 caracteres");
    }
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
    return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
  }

  @Bean
  public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
  }
}
