package tuttibox.hw1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelState;
import tuttibox.hw1.port.HardwarePort;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.ParcelRepository;
import tuttibox.hw1.repository.StationRepository;

class ParcelServiceTest {
    private HardwarePort hardwarePortMock;
    private ParcelRepository parcelRepositoryMock;
    private StationRepository stationRepositoryMock;
    private CompartmentRepository compartmentRepositoryMock;
    private ParcelService parcelService;

    @BeforeEach
    void setUp() {
        hardwarePortMock = Mockito.mock(HardwarePort.class);
        parcelRepositoryMock = Mockito.mock(ParcelRepository.class);
        stationRepositoryMock = Mockito.mock(StationRepository.class);
        compartmentRepositoryMock = Mockito.mock(CompartmentRepository.class);
        parcelService = new ParcelService(
            hardwarePortMock, parcelRepositoryMock,
            stationRepositoryMock, compartmentRepositoryMock);
    }

    @Test 
    @DisplayName("dropoff abre hardware e transiciona parcel para READY_FOR_PICKUP") 
    void testSuccessfulDropOff() {
        Parcel parcel = new Parcel("TEST999", "user@example.com", "S");
        when(parcelRepositoryMock.findByTrackingCode("TEST999")).thenReturn(Optional.of(parcel));
        when(parcelRepositoryMock.save(parcel)).thenReturn(parcel);

        parcelService.processCourierDropOff("TEST999", "STATION_01", "COMP_S1", "COURIER_01");

        verify(hardwarePortMock).openCompartment("STATION_01", "COMP_S1");
        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
    }

    @Test 
    @DisplayName("dropoff com compartimento demasiado pequeno lança IllegalArgumentException")
    void testDropOffSizeMismatch() {
        Parcel parcel = new Parcel("TEST998", "user@example.com", "L");
        when(parcelRepositoryMock.findByTrackingCode("TEST998")).thenReturn(Optional.of(parcel));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            parcelService.processCourierDropOff("TEST998", "STATION_01", "COMP_S1", "COURIER_01")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("muito pequeno");

        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.CREATED);
    }
}
