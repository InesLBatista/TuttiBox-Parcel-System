package tuttibox.hw1.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompartmentTest {
    private Station station;
    private Compartment compartment;

    @BeforeEach
    void setUp() {
        station = new Station("ST-01", "Estação Central", 40.63, -8.65);
        compartment = new Compartment("C-M1", station, CompartmentSize.M);
    }

    @Test
    @DisplayName("novo compartimento começa FREE com os seus atributos definidos")
    void testInitialState() {
        assertThat(compartment.getCode()).isEqualTo("C-M1");
        assertThat(compartment.getStation()).isEqualTo(station);
        assertThat(compartment.getSize()).isEqualTo(CompartmentSize.M);
        assertThat(compartment.getStatus()).isEqualTo(CompartmentStatus.FREE);
    }

    @Test
    @DisplayName("reserve passa um compartimento FREE para RESERVED")
    void testReserve() {
        compartment.reserve();

        assertThat(compartment.getStatus()).isEqualTo(CompartmentStatus.RESERVED);
    }

    @Test
    @DisplayName("reserve falha quando o compartimento não está FREE")
    void testReserveWhenNotFree() {
        compartment.reserve();

        assertThatThrownBy(() -> compartment.reserve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("C-M1")
                .hasMessageContaining("RESERVED");

        assertThat(compartment.getStatus()).isEqualTo(CompartmentStatus.RESERVED);
    }

    @Test
    @DisplayName("occupy passa um compartimento RESERVED para OCCUPIED")
    void testOccupy() {
        compartment.reserve();
        compartment.occupy();

        assertThat(compartment.getStatus()).isEqualTo(CompartmentStatus.OCCUPIED);
    }

    @Test
    @DisplayName("occupy falha quando o compartimento não está RESERVED")
    void testOccupyWhenNotReserved() {
        assertThatThrownBy(() -> compartment.occupy())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("C-M1")
                .hasMessageContaining("FREE");

        assertThat(compartment.getStatus()).isEqualTo(CompartmentStatus.FREE);
    }

    @Test
    @DisplayName("release devolve um compartimento RESERVED a FREE")
    void testReleaseReservedCompartment() {
        compartment.reserve();
        compartment.release();
        assertThat(compartment.getStatus()).isEqualTo(CompartmentStatus.FREE);
    }

    @Test
    @DisplayName("release mantém um compartimento OUT_OF_SERVICE nesse estado")
    void testReleaseOutOfServiceCompartment() {
        compartment.setStatus(CompartmentStatus.OUT_OF_SERVICE);
        compartment.release();
        assertThat(compartment.getStatus()).isEqualTo(CompartmentStatus.OUT_OF_SERVICE);
    }
}
