package tuttibox.hw1.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PickupCodeTest {
    @Test
    @DisplayName("código gerado tem 8 caracteres")
    void testGeneratedCodeFormat() {
        String code = PickupCode.generate();
        assertThat(code).matches("[A-HJ-NP-Z2-9]{8}");
    }

    @Test
    @DisplayName("hash é determinístico e tem 64 caracteres hexadecimais")
    void testHashIsDeterministic() {
        String hash = PickupCode.hash("ABCD2345");

        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(PickupCode.hash("ABCD2345")).isEqualTo(hash);
    }

    @Test
    @DisplayName("matches aceita o código correto e rejeita outro código")
    void testMatches() {
        String hash = PickupCode.hash("ABCD2345");

        assertThat(PickupCode.matches("ABCD2345", hash)).isTrue();
        assertThat(PickupCode.matches("ZZZZ9999", hash)).isFalse();
        assertThat(PickupCode.matches(null, hash)).isFalse();
        assertThat(PickupCode.matches("ABCD2345", null)).isFalse();
    }

    @Test
    @DisplayName("normalize remove espaços e converte para maiúsculas")
    void testNormalize() {
        assertThat(PickupCode.normalize(" abcd2345 ")).isEqualTo("ABCD2345");
        assertThat(PickupCode.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("validação do formato rejeita códigos incompletos ou ambíguos")
    void testFormatValidation() {
        assertThat(PickupCode.isValidFormat("ABCD2345")).isTrue();
        assertThat(PickupCode.isValidFormat("abcd2345")).isFalse();
        assertThat(PickupCode.isValidFormat("ABCD234")).isFalse();
        assertThat(PickupCode.isValidFormat("ABCDI234")).isFalse();
        assertThat(PickupCode.isValidFormat(null)).isFalse();
    }
}
