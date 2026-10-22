package tuttibox.hw1.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tuttibox.hw1.model.Coordinates;

class InMemoryGeocodingFakeTest {
    private InMemoryGeocodingFake geocodingFake;

    @BeforeEach
    void setUp() {
        geocodingFake = new InMemoryGeocodingFake();
    }

    @Test
    @DisplayName("devolve coordenadas para uma morada pré-carregada")
    void testSeededAddress() {
        Optional<Coordinates> coordinates = geocodingFake.getCoordinates("Anadia");

        assertThat(coordinates).isPresent();
        assertThat(coordinates.get().getLatitude()).isEqualTo(40.6305);
        assertThat(coordinates.get().getLongitude()).isEqualTo(-8.6571);
    }

    @Test
    @DisplayName("devolve vazio para uma morada desconhecida")
    void testUnknownAddress() {
        assertThat(geocodingFake.getCoordinates("Desconhecida")).isEmpty();
    }

    @Test
    @DisplayName("moradas adicionadas em runtime podem ser resolvidas")
    void testAddAddress() {
        Coordinates coords = new Coordinates(40.0, -8.0);

        geocodingFake.addAddress("Nova Morada", coords);

        assertThat(geocodingFake.getCoordinates("Nova Morada")).contains(coords);
    }

    @Test
    @DisplayName("indisponibilidade simulada lança uma exceção de runtime")
    void testSimulateUnavailable() {
        geocodingFake.setSimulateUnavailable(true);

        assertThatThrownBy(() -> geocodingFake.getCoordinates("Anadia"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unavailable");
    }
}
