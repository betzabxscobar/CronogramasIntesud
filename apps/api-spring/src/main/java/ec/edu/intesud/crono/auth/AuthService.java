package ec.edu.intesud.crono.auth;

import ec.edu.intesud.crono.common.ApiException;
import ec.edu.intesud.crono.config.CronoProperties;
import ec.edu.intesud.crono.domain.AuditLog;
import ec.edu.intesud.crono.domain.LoginCode;
import ec.edu.intesud.crono.domain.RecordStatus;
import ec.edu.intesud.crono.domain.User;
import ec.edu.intesud.crono.domain.UserSession;
import ec.edu.intesud.crono.mail.MailService;
import ec.edu.intesud.crono.repository.AuditLogRepository;
import ec.edu.intesud.crono.repository.LoginCodeRepository;
import ec.edu.intesud.crono.repository.UserRepository;
import ec.edu.intesud.crono.repository.UserSessionRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Inicio de sesión sin contraseña: se envía por correo un código de 6 dígitos y, al verificarlo,
 * se entrega un token de acceso (JWT corto) y un token de refresco (cookie).
 *
 * <p>Los códigos y los tokens se guardan con los mismos hashes que usaba la API de NestJS, así
 * que las sesiones abiertas allí siguen siendo válidas aquí.
 */
@Service
public class AuthService {

  private static final String GENERIC_REQUEST_MESSAGE =
      "Si la cédula está autorizada, recibirás un código de acceso.";
  private static final String INVALID_CODE_MESSAGE = "Código inválido o vencido";
  private static final String INVALID_SESSION_MESSAGE = "Sesión inválida o vencida";
  private static final Duration CODE_TTL = Duration.ofMinutes(5);
  private static final Duration RESEND_WAIT = Duration.ofMinutes(1);
  private static final int MAX_CODE_ATTEMPTS = 5;

  private final UserRepository users;
  private final LoginCodeRepository loginCodes;
  private final UserSessionRepository sessions;
  private final AuditLogRepository auditLogs;
  private final AuthenticatedUserLoader userLoader;
  private final MailService mail;
  private final JwtEncoder jwtEncoder;
  private final CronoProperties properties;
  private final TransactionTemplate transaction;
  private final SecureRandom random = new SecureRandom();

  public AuthService(
      UserRepository users,
      LoginCodeRepository loginCodes,
      UserSessionRepository sessions,
      AuditLogRepository auditLogs,
      AuthenticatedUserLoader userLoader,
      MailService mail,
      JwtEncoder jwtEncoder,
      CronoProperties properties,
      PlatformTransactionManager transactionManager) {
    this.users = users;
    this.loginCodes = loginCodes;
    this.sessions = sessions;
    this.auditLogs = auditLogs;
    this.userLoader = userLoader;
    this.mail = mail;
    this.jwtEncoder = jwtEncoder;
    this.properties = properties;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  public record SessionMetadata(String ipAddress, String userAgent) {}

  public record AuthResult(String accessToken, String refreshToken, AuthenticatedUser user) {}

  /**
   * Envía un código de acceso. La respuesta es siempre la misma, exista o no la cédula, para no
   * revelar qué cédulas están registradas.
   */
  public String requestCode(String rawNationalId, SessionMetadata metadata) {
    Instant now = Instant.now();
    Optional<User> found = users.findByNationalId(rawNationalId.trim());

    if (found.isEmpty()
        || found.get().getStatus() != RecordStatus.ACTIVE
        || loginCodes.existsByUser_IdAndCreatedAtAfter(
            found.get().getId(), now.minus(RESEND_WAIT))) {
      hashLoginCode("000000"); // mismo trabajo que en el caso válido
      return GENERIC_REQUEST_MESSAGE;
    }
    User user = found.get();

    String code = String.format("%06d", random.nextInt(1_000_000));
    loginCodes.consumePending(user.getId(), now);
    LoginCode loginCode = new LoginCode();
    loginCode.setUser(user);
    loginCode.setCodeHash(hashLoginCode(code));
    loginCode.setExpiresAt(now.plus(CODE_TTL));
    loginCode.setRequestIp(metadata.ipAddress());
    loginCode = loginCodes.save(loginCode);

    boolean sent = mail.sendLoginCode(user.getEmail(), user.getFirstNames(), code);
    if (!sent) {
      loginCodes.consume(loginCode.getId(), Instant.now());
    }
    audit(user, sent ? "AUTH_CODE_SENT" : "AUTH_CODE_DELIVERY_FAILED", metadata);
    return GENERIC_REQUEST_MESSAGE;
  }

  public AuthResult verifyCode(String rawNationalId, String code, SessionMetadata metadata) {
    Instant now = Instant.now();
    Optional<User> found = users.findByNationalId(rawNationalId.trim());
    Optional<LoginCode> pending =
        found.flatMap(
            user ->
                loginCodes.findFirstByUser_IdAndConsumedAtIsNullOrderByCreatedAtDesc(
                    user.getId()));

    if (found.isEmpty()
        || found.get().getStatus() != RecordStatus.ACTIVE
        || pending.isEmpty()
        || !pending.get().getExpiresAt().isAfter(now)
        || pending.get().getAttempts() >= MAX_CODE_ATTEMPTS) {
      hashLoginCode(code);
      throw ApiException.unauthorized(INVALID_CODE_MESSAGE);
    }
    User user = found.get();
    UUID loginCodeId = pending.get().getId();

    if (!Hashes.matches(hashLoginCode(code), pending.get().getCodeHash())) {
      transaction.executeWithoutResult(
          status -> {
            loginCodes.incrementAttempts(loginCodeId);
            loginCodes.consumeIfExhausted(loginCodeId, MAX_CODE_ATTEMPTS, now);
          });
      throw ApiException.unauthorized(INVALID_CODE_MESSAGE);
    }

    transaction.executeWithoutResult(
        status -> {
          loginCodes.consume(loginCodeId, now);
          users.registerLogin(user.getId(), now);
        });

    String accessToken = createAccessToken(user.getId());
    String refreshToken = createSession(user, metadata);
    audit(user, "AUTH_LOGIN", metadata);
    return new AuthResult(accessToken, refreshToken, userLoader.toAuthenticatedUser(user));
  }

  /** Renueva la sesión y cambia el token de refresco (el anterior deja de servir). */
  public AuthResult refresh(String rawToken) {
    RefreshToken parsed = parseRefreshToken(rawToken);
    Optional<UserSession> found =
        parsed == null ? Optional.empty() : sessions.findWithUserById(parsed.sessionId());

    if (found.isEmpty()
        || found.get().getRevokedAt() != null
        || !found.get().getExpiresAt().isAfter(Instant.now())
        || found.get().getUser().getStatus() != RecordStatus.ACTIVE
        || !Hashes.matches(Hashes.sha256Hex(parsed.secret()), found.get().getRefreshTokenHash())) {
      throw ApiException.unauthorized(INVALID_SESSION_MESSAGE);
    }
    UserSession session = found.get();

    String nextSecret = randomSecret();
    sessions.rotate(session.getId(), Hashes.sha256Hex(nextSecret));
    return new AuthResult(
        createAccessToken(session.getUser().getId()),
        session.getId() + "." + nextSecret,
        userLoader.toAuthenticatedUser(session.getUser()));
  }

  public void logout(String rawToken) {
    RefreshToken parsed = parseRefreshToken(rawToken);
    if (parsed != null) {
      sessions.revoke(parsed.sessionId(), Instant.now());
    }
  }

  private String createAccessToken(UUID userId) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(userId.toString())
            .claim("type", "access")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(properties.accessTokenTtlSeconds()))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private String createSession(User user, SessionMetadata metadata) {
    String secret = randomSecret();
    UserSession session = new UserSession();
    session.setUser(user);
    session.setRefreshTokenHash(Hashes.sha256Hex(secret));
    session.setExpiresAt(Instant.now().plus(Duration.ofHours(properties.sessionHours())));
    session.setIpAddress(metadata.ipAddress());
    session.setUserAgent(metadata.userAgent());
    session = sessions.save(session);
    return session.getId() + "." + secret;
  }

  private void audit(User user, String action, SessionMetadata metadata) {
    AuditLog entry = new AuditLog();
    entry.setUser(user);
    entry.setAction(action);
    entry.setEntityType("User");
    entry.setEntityId(user.getId().toString());
    entry.setIpAddress(metadata.ipAddress());
    entry.setUserAgent(metadata.userAgent());
    auditLogs.save(entry);
  }

  private record RefreshToken(UUID sessionId, String secret) {}

  /** El token de refresco tiene la forma "idDeSesion.secreto". */
  private RefreshToken parseRefreshToken(String rawToken) {
    if (rawToken == null) return null;
    int separator = rawToken.indexOf('.');
    if (separator < 1) return null;
    try {
      return new RefreshToken(
          UUID.fromString(rawToken.substring(0, separator)), rawToken.substring(separator + 1));
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private String randomSecret() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** HMAC-SHA256 del código con SESSION_SECRET. */
  private String hashLoginCode(String code) {
    return Hashes.hmacSha256Hex(properties.sessionSecret(), code);
  }
}
