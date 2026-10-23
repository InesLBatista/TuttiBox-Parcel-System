package tuttibox.hw1.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import tuttibox.hw1.model.Coordinates;
import tuttibox.hw1.port.GeocodingException;

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

    @ParameterizedTest
    @EnumSource(GeocodingException.Reason.class)
    @DisplayName("simula cada tipo de falha do serviço de geocoding")
    void testSimulatedFailures(GeocodingException.Reason reason) {
        geocodingFake.setFailure(reason);

        assertThatThrownBy(() -> geocodingFake.getCoordinates("Anadia"))
                .isInstanceOf(GeocodingException.class)
                .satisfies(ex -> assertThat(((GeocodingException) ex).getReason()).isEqualTo(reason));
    }

    @Test
    @DisplayName("indisponibilidade simulada lança uma exceção de geocoding")
    void testSimulateUnavailable() {
        geocodingFake.setSimulateUnavailable(true);

        assertThatThrownBy(() -> geocodingFake.getCoordinates("Anadia"))
                .isInstanceOf(GeocodingException.class)
                .satisfies(ex -> assertThat(((GeocodingException) ex).getReason())
                    .isEqualTo(GeocodingException.Reason.UNAVAILABLE))
                .hasMessageContaining("unavailable");
    }

    @Test
    @DisplayName("falha simulada pode ser removida")
    void testClearSimulatedFailure() {
        geocodingFake.setFailure(GeocodingException.Reason.TIMEOUT);
        geocodingFake.clearFailure();

        assertThat(geocodingFake.getCoordinates("Anadia")).isPresent();
    }
}
