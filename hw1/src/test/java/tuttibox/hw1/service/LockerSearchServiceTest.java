package tuttibox.hw1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import tuttibox.hw1.dto.NearbyStationDto;
import tuttibox.hw1.fake.InMemoryGeocodingFake;
import tuttibox.hw1.model.Coordinates;
import tuttibox.hw1.model.Station;
import tuttibox.hw1.repository.StationRepository;

class LockerSearchServiceTest {
    private InMemoryGeocodingFake geocodingFake;
    private StationRepository stationRepositoryMock;
    private LockerSearchService lockerSearchService;

    @BeforeEach
    void setUp() {
        geocodingFake = new InMemoryGeocodingFake();
        stationRepositoryMock = Mockito.mock(StationRepository.class);
        lockerSearchService = new LockerSearchService(geocodingFake, stationRepositoryMock);
    }

    @Test
    @DisplayName("deve ordenar as estações da mais próxima para a mais distante")
    void testStationsAreRankedByDistance() {
        Station near = new Station("ST-NEAR", "Estação Perto", 40.6305, -8.6571);
        Station mid = new Station("ST-MID", "Estação Média", 40.6418, -8.6444);
        Station far = new Station("ST-FAR", "Estação Longe", 38.7223, -9.1393);
        when(stationRepositoryMock.findAll()).thenReturn(List.of(far, near, mid));

        List<NearbyStationDto> result = lockerSearchService.findNearestStations("Anadia");

        assertThat(result)
            .extracting(NearbyStationDto::code)
            .containsExactly("ST-NEAR", "ST-MID", "ST-FAR");
        assertThat(result)
            .extracting(NearbyStationDto::distanceKm)
            .isSorted();
        assertThat(result.get(0).distanceKm()).isZero();
        assertThat(result.get(0).name()).isEqualTo("Estação Perto");
        assertThat(result.get(0).latitude()).isEqualTo(40.6305);
        assertThat(result.get(0).longitude()).isEqualTo(-8.6571);
    }

    @Test
    @DisplayName("deve devolver lista vazia quando não existem estações")
    void testNoStations() {
        when(stationRepositoryMock.findAll()).thenReturn(List.of());

        assertThat(lockerSearchService.findNearestStations("Anadia")).isEmpty();
    }

    @Test
    @DisplayName("deve devolver lista vazia quando a morada não é encontrada")
    void testAddressNotFound() {
        assertThat(lockerSearchService.findNearestStations("morada inexistente")).isEmpty();
        verifyNoInteractions(stationRepositoryMock);
    }

    @Test
    @DisplayName("deve lidar com a indisponibilidade do serviço de geocoding")
    void testGeocodingServiceUnavailable() {
        geocodingFake.setSimulateUnavailable(true);

        assertThatThrownBy(() -> lockerSearchService.findNearestStations("Anadia"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("geocoding service is unavailable");

        verifyNoInteractions(stationRepositoryMock);
    }

    @Test
    @DisplayName("distância de um grau de longitude no equador é aproximadamente 111,195 km")
    void testHaversineKnownDistance() {
        double distance = LockerSearchService.distanceKm(
            new Coordinates(0, 0), new Coordinates(0, 1));

        assertThat(distance).isCloseTo(111.195, within(0.01));
    }

    @Test
    @DisplayName("distância entre coordenadas iguais é zero")
    void testHaversineSamePoint() {
        Coordinates point = new Coordinates(40.6305, -8.6571);

        assertThat(LockerSearchService.distanceKm(point, point)).isZero();
    }

    @Test
    @DisplayName("distância é simétrica")
    void testHaversineIsSymmetric() {
        Coordinates from = new Coordinates(40.6305, -8.6571);
        Coordinates to = new Coordinates(38.7223, -9.1393);

        assertThat(LockerSearchService.distanceKm(from, to))
            .isCloseTo(LockerSearchService.distanceKm(to, from), within(0.000001));
    }
}
