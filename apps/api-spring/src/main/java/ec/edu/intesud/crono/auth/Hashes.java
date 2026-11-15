package ec.edu.intesud.crono.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Hashes con los que se guardan los códigos de acceso y los tokens de refresco. Producen
 * exactamente el mismo resultado que el módulo crypto de Node que usaba la API de NestJS.
 */
public final class Hashes {

  private Hashes() {}

  /** Equivale a createHmac("sha256", secret).update(value).digest("hex"). */
  public static String hmacSha256Hex(String secret, String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(exception);
    }
  }

  /** Equivale a createHash("sha256").update(value).digest("hex"). */
  public static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(exception);
    }
  }

  /** Comparación en tiempo constante, para no filtrar información por el tiempo de respuesta. */
  public static boolean matches(String receivedHash, String expectedHash) {
    return MessageDigest.isEqual(
        receivedHash.getBytes(StandardCharsets.US_ASCII),
        expectedHash.toLowerCase().getBytes(StandardCharsets.US_ASCII));
  }
}
