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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import tuttibox.hw1.dto.NearbyStationDto;
import tuttibox.hw1.fake.InMemoryGeocodingFake;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.CompartmentStatus;
import tuttibox.hw1.model.Coordinates;
import tuttibox.hw1.model.Station;
import tuttibox.hw1.port.GeocodingException;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.StationRepository;

class StationSearchServiceTest {
    private InMemoryGeocodingFake geocodingFake;
    private StationRepository stationRepositoryMock;
    private CompartmentRepository compartmentRepositoryMock;
    private StationSearchService stationSearchService;

    @BeforeEach
    void setUp() {
        geocodingFake = new InMemoryGeocodingFake();
        stationRepositoryMock = Mockito.mock(StationRepository.class);
        compartmentRepositoryMock = Mockito.mock(CompartmentRepository.class);
        stationSearchService = new StationSearchService(
            geocodingFake, stationRepositoryMock, compartmentRepositoryMock);
    }

    @Test
    @DisplayName("deve ordenar as estações da mais próxima para a mais distante")
    void testStationsAreRankedByDistance() {
        Station near = new Station("ST-NEAR", "Estação Perto", 40.6305, -8.6571);
        Station mid = new Station("ST-MID", "Estação Média", 40.6418, -8.6444);
        Station far = new Station("ST-FAR", "Estação Longe", 38.7223, -9.1393);
        when(stationRepositoryMock.findAll()).thenReturn(List.of(far, near, mid));

        List<NearbyStationDto> result = stationSearchService.findNearestStations("Anadia");

        assertThat(result)
            .extracting(dto -> java.util.Objects.requireNonNull(dto).code())
            .containsExactly("ST-NEAR", "ST-MID", "ST-FAR");
        assertThat(result)
            .extracting(dto -> java.util.Objects.requireNonNull(dto).distanceKm())
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

        assertThat(stationSearchService.findNearestStations("Anadia")).isEmpty();
        verifyNoInteractions(compartmentRepositoryMock);
    }

    @Test
    @DisplayName("deve devolver lista vazia quando a morada não é encontrada")
    void testAddressNotFound() {
        assertThat(stationSearchService.findNearestStations("morada inexistente")).isEmpty();
        verifyNoInteractions(stationRepositoryMock);
        verifyNoInteractions(compartmentRepositoryMock);
    }

    @ParameterizedTest
    @EnumSource(GeocodingException.Reason.class)
    @DisplayName("deve propagar falhas do geocoding sem consultar os repositórios")
    void testGeocodingFailuresArePropagated(GeocodingException.Reason reason) {
        geocodingFake.setFailure(reason);

        assertThatThrownBy(() -> stationSearchService.findNearestStations("Anadia"))
            .isInstanceOf(GeocodingException.class)
            .satisfies(ex -> assertThat(((GeocodingException) ex).getReason()).isEqualTo(reason));

        verifyNoInteractions(stationRepositoryMock);
        verifyNoInteractions(compartmentRepositoryMock);
    }

    @Test
    @DisplayName("distância entre coordenadas iguais é zero")
    void testHaversineSamePoint() {
        Coordinates point = new Coordinates(40.6305, -8.6571);

        assertThat(StationSearchService.distanceKm(point, point)).isZero();
    }

    @Test
    @DisplayName("distância é simétrica")
    void testHaversineIsSymmetric() {
        Coordinates from = new Coordinates(40.6305, -8.6571);
        Coordinates to = new Coordinates(38.7223, -9.1393);

        assertThat(StationSearchService.distanceKm(from, to))
            .isCloseTo(StationSearchService.distanceKm(to, from), within(0.000001));
    }

    @Test
    @DisplayName("deve incluir o número de compartimentos livres por tamanho em cada estação")
    void testAvailabilityPerSizePerStation() {
        Station near = new Station("ST-NEAR", "Estação Perto", 40.6305, -8.6571);
        Station far = new Station("ST-FAR", "Estação Longe", 38.7223, -9.1393);
        when(stationRepositoryMock.findAll()).thenReturn(List.of(near, far));
        when(compartmentRepositoryMock.countByStationCodesAndStatusGroupedBySize(
                List.of("ST-NEAR", "ST-FAR"), CompartmentStatus.FREE))
            .thenReturn(List.of(
                projection("ST-NEAR", CompartmentSize.S, 2L),
                projection("ST-NEAR", CompartmentSize.M, 1L),
                projection("ST-FAR", CompartmentSize.L, 4L)));

        List<NearbyStationDto> result = stationSearchService.findNearestStations("Anadia");

        NearbyStationDto nearDto = result.get(0);
        assertThat(nearDto.code()).isEqualTo("ST-NEAR");
        assertThat(nearDto.availableSmall()).isEqualTo(2L);
        assertThat(nearDto.availableMedium()).isEqualTo(1L);
        assertThat(nearDto.availableLarge()).isZero();

        NearbyStationDto farDto = result.get(1);
        assertThat(farDto.code()).isEqualTo("ST-FAR");
        assertThat(farDto.availableSmall()).isZero();
        assertThat(farDto.availableMedium()).isZero();
        assertThat(farDto.availableLarge()).isEqualTo(4L);
    }

    @Test
    @DisplayName("deve devolver zeros de disponibilidade quando a estação não tem compartimentos livres")
    void testAvailabilityDefaultsToZero() {
        Station station = new Station("ST-EMPTY", "Estação Vazia", 40.6305, -8.6571);
        when(stationRepositoryMock.findAll()).thenReturn(List.of(station));
        when(compartmentRepositoryMock.countByStationCodesAndStatusGroupedBySize(
                List.of("ST-EMPTY"), CompartmentStatus.FREE))
            .thenReturn(List.of());

        NearbyStationDto dto = stationSearchService.findNearestStations("Anadia").get(0);

        assertThat(dto.availableSmall()).isZero();
        assertThat(dto.availableMedium()).isZero();
        assertThat(dto.availableLarge()).isZero();
    }

    private static tuttibox.hw1.repository.CompartmentCountProjection projection(
        String stationCode, CompartmentSize size, long total) {
        return new tuttibox.hw1.repository.CompartmentCountProjection() {
            @Override
            public String getStationCode() {
                return stationCode;
            }

            @Override
            public CompartmentSize getSize() {
                return size;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
