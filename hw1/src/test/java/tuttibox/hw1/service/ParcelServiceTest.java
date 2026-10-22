package tuttibox.hw1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import tuttibox.hw1.dto.ParcelRequestDto;
import tuttibox.hw1.dto.ParcelResponseDto;
import tuttibox.hw1.exception.InvalidTransitionException;
import tuttibox.hw1.exception.NoAvailableCompartmentException;
import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.CompartmentStatus;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelState;
import tuttibox.hw1.model.ParcelTransition;
import tuttibox.hw1.model.Station;
import tuttibox.hw1.port.HardwareException;
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

    @Test
    @DisplayName("dropoff de encomenda inexistente lança EntityNotFoundException")
    void testDropOffUnknownParcel() {
        when(parcelRepositoryMock.findByTrackingCode("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            parcelService.processCourierDropOff("MISSING", "STATION_01", "COMP_S1", "COURIER_01")
        ).isInstanceOf(EntityNotFoundException.class)
         .hasMessageContaining("MISSING");

        verifyNoInteractions(hardwarePortMock);
    }

    @Test
    @DisplayName("dropoff infere tamanho a partir de identificadores com nome do tamanho")
    void testDropOffInfersNamedCompartmentSize() {
        Parcel parcel = new Parcel("TEST997", "user@example.com", "M");
        when(parcelRepositoryMock.findByTrackingCode("TEST997")).thenReturn(Optional.of(parcel));
        when(parcelRepositoryMock.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Parcel result = parcelService.processCourierDropOff(
            "TEST997", "STATION_01", "MEDIUM_1", "COURIER_01");

        assertThat(result.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        verify(hardwarePortMock).openCompartment("STATION_01", "MEDIUM_1");
    }

    @Test
    @DisplayName("pickup abre o compartimento e transiciona para PICKED_UP")
    void testSuccessfulRecipientPickUp() {
        Parcel parcel = new Parcel("TEST996", "user@example.com", "M");
        parcel.markAsReadyForPickup("COURIER_01");
        when(parcelRepositoryMock.findByTrackingCode("TEST996")).thenReturn(Optional.of(parcel));
        when(parcelRepositoryMock.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Parcel result = parcelService.processRecipientPickUp(
            "TEST996", "STATION_01", "COMP_M1", "RECIPIENT_01");

        verify(hardwarePortMock).openCompartment("STATION_01", "COMP_M1");
        assertThat(result.getCurrentState()).isEqualTo(ParcelState.PICKED_UP);
        assertThat(result.getTransitions()).hasSize(3);
    }

    @Test
    @DisplayName("falha de hardware no pickup mantém a parcela em READY_FOR_PICKUP")
    void testHardwareFailureDuringPickUp() {
        Parcel parcel = new Parcel("TEST995", "user@example.com", "S");
        parcel.markAsReadyForPickup("COURIER_01");
        when(parcelRepositoryMock.findByTrackingCode("TEST995")).thenReturn(Optional.of(parcel));
        doThrow(new HardwareException("hardware failure"))
            .when(hardwarePortMock).openCompartment("STATION_01", "COMP_S1");

        assertThatThrownBy(() ->
            parcelService.processRecipientPickUp("TEST995", "STATION_01", "COMP_S1", "RECIPIENT_01")
        ).isInstanceOf(HardwareException.class);

        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        verify(parcelRepositoryMock, never()).save(any(Parcel.class));
    }

    @Test
    @DisplayName("histórico devolve as transições da encomenda")
    void testGetParcelHistory() {
        Parcel parcel = new Parcel("TEST994", "user@example.com", "S");
        parcel.markAsReadyForPickup("COURIER_01");
        when(parcelRepositoryMock.findByTrackingCode("TEST994")).thenReturn(Optional.of(parcel));

        List<ParcelTransition> history = parcelService.getParcelHistory("TEST994");

        assertThat(history).hasSize(2);
        assertThat(history.get(1).getToState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
    }

    @Test
    @DisplayName("histórico de encomenda inexistente lança EntityNotFoundException")
    void testGetParcelHistoryUnknownParcel() {
        when(parcelRepositoryMock.findByTrackingCode("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parcelService.getParcelHistory("MISSING"))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("cancelamento liberta o compartimento reservado")
    void testCancellationReleasesCompartment() {
        Station station = new Station("ST-01", "Estação Central", 40.63, -8.65);
        Compartment compartment = new Compartment("C-M1", station, CompartmentSize.M);
        compartment.reserve();
        Parcel parcel = new Parcel("TEST993", "user@example.com", "M");
        parcel.setStation(station);
        parcel.setAssignedCompartment(compartment);
        when(parcelRepositoryMock.findByTrackingCode("TEST993")).thenReturn(Optional.of(parcel));
        when(parcelRepositoryMock.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Parcel result = parcelService.processParcelCancellation("TEST993", "PARTNER_01");

        assertThat(result.getCurrentState()).isEqualTo(ParcelState.CANCELLED);
        assertThat(compartment.getStatus()).isEqualTo(CompartmentStatus.FREE);
        verify(compartmentRepositoryMock).save(compartment);
    }

    @Test
    @DisplayName("cancelamento sem compartimento associado não interage com o repositório de compartimentos")
    void testCancellationWithoutCompartment() {
        Parcel parcel = new Parcel("TEST992", "user@example.com", "S");
        when(parcelRepositoryMock.findByTrackingCode("TEST992")).thenReturn(Optional.of(parcel));
        when(parcelRepositoryMock.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Parcel result = parcelService.processParcelCancellation("TEST992", "PARTNER_01");

        assertThat(result.getCurrentState()).isEqualTo(ParcelState.CANCELLED);
        verifyNoInteractions(compartmentRepositoryMock);
    }

    @Test
    @DisplayName("cancelamento inválido não liberta o compartimento")
    void testInvalidCancellationDoesNotReleaseCompartment() {
        Station station = new Station("ST-01", "Estação Central", 40.63, -8.65);
        Compartment compartment = new Compartment("C-M1", station, CompartmentSize.M);
        compartment.reserve();
        Parcel parcel = new Parcel("TEST991", "user@example.com", "M");
        parcel.setAssignedCompartment(compartment);
        parcel.markAsReadyForPickup("COURIER_01");
        when(parcelRepositoryMock.findByTrackingCode("TEST991")).thenReturn(Optional.of(parcel));

        assertThatThrownBy(() -> parcelService.processParcelCancellation("TEST991", "PARTNER_01"))
            .isInstanceOf(InvalidTransitionException.class);

        assertThat(compartment.getStatus()).isEqualTo(CompartmentStatus.RESERVED);
        verify(compartmentRepositoryMock, never()).save(any(Compartment.class));
    }

    @Test
    @DisplayName("expiração transiciona para EXPIRED")
    void testParcelExpiration() {
        Parcel parcel = new Parcel("TEST990", "user@example.com", "S");
        parcel.markAsReadyForPickup("COURIER_01");
        when(parcelRepositoryMock.findByTrackingCode("TEST990")).thenReturn(Optional.of(parcel));
        when(parcelRepositoryMock.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Parcel result = parcelService.processParcelExpiration("TEST990", "SYSTEM");

        assertThat(result.getCurrentState()).isEqualTo(ParcelState.EXPIRED);
    }

    @Test
    @DisplayName("devolução transiciona para RETURNED")
    void testParcelReturn() {
        Parcel parcel = new Parcel("TEST989", "user@example.com", "S");
        parcel.markAsReadyForPickup("COURIER_01");
        parcel.expire("SYSTEM");
        when(parcelRepositoryMock.findByTrackingCode("TEST989")).thenReturn(Optional.of(parcel));
        when(parcelRepositoryMock.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Parcel result = parcelService.processParcelReturn("TEST989", "OPERATOR");

        assertThat(result.getCurrentState()).isEqualTo(ParcelState.RETURNED);
    }

    @Test
    @DisplayName("registo falha quando a estação não existe")
    void testRegisterParcelUnknownStation() {
        ParcelRequestDto request = buildRegisterRequest("ST-INEXISTENTE", "S");
        when(stationRepositoryMock.findByCode("ST-INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parcelService.registerParcel(request))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("ST-INEXISTENTE");

        verifyNoInteractions(compartmentRepositoryMock);
    }

    @Test
    @DisplayName("registo falha quando não há compartimento compatível disponível")
    void testRegisterParcelNoAvailableCompartment() {
        Station station = new Station("ST-01", "Estação Central", 40.63, -8.65);
        ParcelRequestDto request = buildRegisterRequest("ST-01", "L");
        when(stationRepositoryMock.findByCode("ST-01")).thenReturn(Optional.of(station));
        when(compartmentRepositoryMock.findAvailableByStationAndSizes(
                eq("ST-01"), eq(CompartmentStatus.FREE), anyList()))
            .thenReturn(List.of());

        assertThatThrownBy(() -> parcelService.registerParcel(request))
            .isInstanceOf(NoAvailableCompartmentException.class)
            .hasMessageContaining("sem compartimentos disponíveis");

        verify(compartmentRepositoryMock, never()).save(any(Compartment.class));
        verify(parcelRepositoryMock, never()).save(any(Parcel.class));
    }

    @Test
    @DisplayName("registo reserva o compartimento e cria a encomenda em CREATED")
    void testRegisterParcelReservesCompartment() {
        Station station = new Station("ST-01", "Estação Central", 40.63, -8.65);
        Compartment compartment = new Compartment("C-M1", station, CompartmentSize.M);
        ParcelRequestDto request = buildRegisterRequest("ST-01", "M");
        when(stationRepositoryMock.findByCode("ST-01")).thenReturn(Optional.of(station));
        when(compartmentRepositoryMock.findAvailableByStationAndSizes(
                eq("ST-01"), eq(CompartmentStatus.FREE), anyList()))
            .thenReturn(List.of(compartment));
        when(parcelRepositoryMock.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ParcelResponseDto response = parcelService.registerParcel(request);

        assertThat(compartment.getStatus()).isEqualTo(CompartmentStatus.RESERVED);
        assertThat(response.trackingCode()).startsWith("TRK-");
        assertThat(response.currentState()).isEqualTo(ParcelState.CREATED);
        assertThat(response.compartmentSize()).isEqualTo("M");
        assertThat(response.assignedCompartmentCode()).isEqualTo("C-M1");
        assertThat(response.assignedStationCode()).isEqualTo("ST-01");
        verify(compartmentRepositoryMock).save(compartment);
        verify(parcelRepositoryMock).save(any(Parcel.class));
    }

    private ParcelRequestDto buildRegisterRequest(String stationId, String size) {
        ParcelRequestDto request = new ParcelRequestDto();
        request.setStationId(stationId);
        request.setCompartmentSize(size);
        request.setRecipientContact("destinatario@example.com");
        request.setPartnerId("PARTNER_01");
        return request;
    }
}
