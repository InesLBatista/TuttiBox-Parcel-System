package tuttibox.hw1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tuttibox.hw1.fake.InMemoryGeocodingFake;
import tuttibox.hw1.model.Coordinates;

class LockerSearchServiceTest {
    private InMemoryGeocodingFake geocodingFake;
    private LockerSearchService lockerSearchService;

    @BeforeEach
    void setUp() {
        geocodingFake = new InMemoryGeocodingFake();
        lockerSearchService = new LockerSearchService(geocodingFake);
    }

    @Test
    @DisplayName("should return coordinates for a valid registered address")
    void testFindCoordinatesSuccess() {
        Optional<Coordinates> coords = lockerSearchService.findNearestStations("Anadia");
        assertThat(coords).isPresent();
        assertThat(coords.get().getLatitude()).isEqualTo(40.6305);
        assertThat(coords.get().getLongitude()).isEqualTo(-8.6571);
    }

    @Test
    @DisplayName("should return empty when address is not found")
    void testAddressNotFound() {
        Optional<Coordinates> coords = lockerSearchService.findNearestStations("morada inexistente");
        assertThat(coords).isEmpty();
    }

    @Test
    @DisplayName("should handle geocoding service unavailability")
    void testGeocodingServiceUnavailable() {
        geocodingFake.setSimulateUnavailable(true);
        assertThatThrownBy(() -> lockerSearchService.findNearestStations("Anadia"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("geocoding service is unavailable");
    }
}
