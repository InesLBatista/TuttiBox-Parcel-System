package tuttibox.hw1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelState;
import tuttibox.hw1.port.HardwarePort;
import tuttibox.hw1.repository.ParcelRepository;

class ParcelServiceTest {
    private HardwarePort hardwarePortMock;
    private ParcelService parcelService;
    private Parcel parcel;
    private ParcelRepository parcelRepositoryMock;
    
    @BeforeEach 
    void setUp() {
        hardwarePortMock = Mockito.mock(HardwarePort.class);
        parcelRepositoryMock = Mockito.mock(ParcelRepository.class);
        parcelService = new ParcelService(hardwarePortMock, parcelRepositoryMock);
        parcel = new Parcel("TEST999", "user@example.com", "S");
    }

    @Test 
    @DisplayName ("drop-off should open hardware compartment and transition parcel to READY_FOR_PICKUP")
    void testSuccessfulDropOff() {
        parcelService.dropOffParcel(parcel, "STATION_01", "COMP_A", "COURIER_01");
        //verificar se hardware foi acionado para de facto abrir o compartimento
        verify(hardwarePortMock).openCompartment("STATION_01", "COMP_A");
        //verificar se estado mudou completamente
        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
    }
}
