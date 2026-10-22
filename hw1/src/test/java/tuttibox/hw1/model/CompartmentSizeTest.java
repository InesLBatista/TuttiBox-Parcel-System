package tuttibox.hw1.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompartmentSizeTest {

    @Test
    @DisplayName("canHold devolve true para o mesmo tamanho e compartimentos maiores")
    void testCanHold() {
        assertThat(CompartmentSize.S.canHold(CompartmentSize.S)).isTrue();
        assertThat(CompartmentSize.M.canHold(CompartmentSize.S)).isTrue();
        assertThat(CompartmentSize.M.canHold(CompartmentSize.M)).isTrue();
        assertThat(CompartmentSize.L.canHold(CompartmentSize.S)).isTrue();
        assertThat(CompartmentSize.L.canHold(CompartmentSize.M)).isTrue();
        assertThat(CompartmentSize.L.canHold(CompartmentSize.L)).isTrue();
    }

    @Test
    @DisplayName("canHold devolve false quando o compartimento é menor que a encomenda")
    void testCannotHold() {
        assertThat(CompartmentSize.S.canHold(CompartmentSize.M)).isFalse();
        assertThat(CompartmentSize.S.canHold(CompartmentSize.L)).isFalse();
        assertThat(CompartmentSize.M.canHold(CompartmentSize.L)).isFalse();
    }

    @Test
    @DisplayName("parse aceita minúsculas, maiúsculas e espaços")
    void testParseValidValues() {
        assertThat(CompartmentSize.parse("s")).isEqualTo(CompartmentSize.S);
        assertThat(CompartmentSize.parse("M")).isEqualTo(CompartmentSize.M);
        assertThat(CompartmentSize.parse("  l  ")).isEqualTo(CompartmentSize.L);
    }

    @Test
    @DisplayName("parse rejeita tamanhos inválidos")
    void testParseInvalidValue() {
        assertThatThrownBy(() -> CompartmentSize.parse("XL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("XL");
    }

    @Test
    @DisplayName("parse rejeita null")
    void testParseNull() {
        assertThatThrownBy(() -> CompartmentSize.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }
}
