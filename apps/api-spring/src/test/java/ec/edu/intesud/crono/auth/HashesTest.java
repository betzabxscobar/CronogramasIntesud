package ec.edu.intesud.crono.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Los valores esperados se calcularon con el módulo crypto de Node (el que usaba NestJS). */
class HashesTest {

  private static final String SECRET = "test-secret-with-at-least-32-characters!!";

  @Test
  void loginCodeHashMatchesNode() {
    assertThat(Hashes.hmacSha256Hex(SECRET, "123456"))
        .isEqualTo("ba50acf1f4ea5b621290e94a27c792cc1a401cf9ae0aa83a37921b9041573722");
  }

  @Test
  void refreshTokenHashMatchesNode() {
    assertThat(Hashes.sha256Hex("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Test
  void matchesComparesHashes() {
    String hash = Hashes.sha256Hex("abc");
    assertThat(Hashes.matches(hash, hash.toUpperCase())).isTrue();
    assertThat(Hashes.matches(hash, Hashes.sha256Hex("abd"))).isFalse();
  }
}
