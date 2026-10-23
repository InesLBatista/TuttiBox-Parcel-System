package tuttibox.hw1.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tuttibox.hw1.port.HardwareException;

class InMemoryHardwareFakeTest {
    private InMemoryHardwareFake hardwareFake;

    @BeforeEach
    void setUp() {
        hardwareFake = new InMemoryHardwareFake();
    }

    @Test
    @DisplayName("portas são reportadas como fechadas por omissão")
    void testDoorClosedByDefault() {
        assertThat(hardwareFake.isDoorClosed("ST-01", "C-1")).isTrue();
    }

    @Test
    @DisplayName("abrir um compartimento reporta a porta como aberta")
    void testOpenCompartment() {
        hardwareFake.openCompartment("ST-01", "C-1");

        assertThat(hardwareFake.isDoorClosed("ST-01", "C-1")).isFalse();
        assertThat(hardwareFake.isDoorClosed("ST-01", "C-2")).isTrue();
    }

    @Test
    @DisplayName("falha de hardware injetada lança HardwareException")
    void testInjectedFailure() {
        hardwareFake.setInjectFailure(true);

        assertThatThrownBy(() -> hardwareFake.openCompartment("ST-01", "C-1"))
                .isInstanceOf(HardwareException.class)
                .hasMessageContaining("hardware failure")
                .hasMessageContaining("C-1");

        assertThat(hardwareFake.isDoorClosed("ST-01", "C-1")).isTrue();
    }

    @Test
    @DisplayName("injeção de falha pode ser desativada novamente")
    void testFailureInjectionCanBeDisabled() {
        hardwareFake.setInjectFailure(true);
        hardwareFake.setInjectFailure(false);

        hardwareFake.openCompartment("ST-01", "C-1");

        assertThat(hardwareFake.isDoorClosed("ST-01", "C-1")).isFalse();
    }

    @Test
    @DisplayName("confirmar fecho marca a porta como fechada")
    void testConfirmDoorClosed() {
        hardwareFake.openCompartment("ST-01", "C-1");
        hardwareFake.confirmDoorClosed("ST-01", "C-1");
        assertThat(hardwareFake.isDoorClosed("ST-01", "C-1")).isTrue();
    }

    @Test
    @DisplayName("falha injetada na confirmação de fecho lança HardwareException")
    void testInjectedCloseFailure() {
        hardwareFake.openCompartment("ST-01", "C-1");
        hardwareFake.setInjectCloseFailure(true);
        assertThatThrownBy(() -> hardwareFake.confirmDoorClosed("ST-01", "C-1"))
                .isInstanceOf(HardwareException.class)
                .hasMessageContaining("hardware failure")
                .hasMessageContaining("C-1");

        assertThat(hardwareFake.isDoorClosed("ST-01", "C-1")).isFalse();
    }

    @Test
    @DisplayName("porta que não fecha permanece aberta após confirmação")
    void testDoorNotClosedInjection() {
        hardwareFake.openCompartment("ST-01", "C-1");
        hardwareFake.setInjectDoorNotClosed(true);
        hardwareFake.confirmDoorClosed("ST-01", "C-1");
        assertThat(hardwareFake.isDoorClosed("ST-01", "C-1")).isFalse();
    }
}
