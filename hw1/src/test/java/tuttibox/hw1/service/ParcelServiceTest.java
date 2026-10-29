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

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import tuttibox.hw1.config.DeadlineProperties;
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
import tuttibox.hw1.port.NotificationPort;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.ParcelRepository;
import tuttibox.hw1.repository.StationRepository;

class ParcelServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 10, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private HardwarePort hardwarePortMock;
    private ParcelRepository parcelRepositoryMock;
    private StationRepository stationRepositoryMock;
    private CompartmentRepository compartmentRepositoryMock;
    private NotificationPort notificationPortMock;
    private DeadlineProperties deadlineProperties;
    private ParcelService parcelService;

    @BeforeEach
    void setUp() {
        hardwarePortMock = Mockito.mock(HardwarePort.class);
        parcelRepositoryMock = Mockito.mock(ParcelRepository.class);
        stationRepositoryMock = Mockito.mock(StationRepository.class);
        compartmentRepositoryMock = Mockito.mock(CompartmentRepository.class);
        notificationPortMock = Mockito.mock(NotificationPort.class);
        deadlineProperties = new DeadlineProperties();
        deadlineProperties.setDropOff(Duration.ofHours(2));
        deadlineProperties.setPickup(Duration.ofHours(24));
        parcelService = new ParcelService(
            hardwarePortMock, parcelRepositoryMock,
            stationRepositoryMock, compartmentRepositoryMock, notificationPortMock,
            FIXED_CLOCK, deadlineProperties);
    }

    @Test 
    @DisplayName("dropoff abre hardware e mantém parcel em CREATED até confirmação de fecho") 
    void testDropOffOpensCompartment() {
        Parcel parcel = parcelWithAssignedCompartment("TEST999", "user@example.com", "S", "COMP_S1", "STATION_01");
        when(parcelRepositoryMock.findByTrackingCode("TEST999")).thenReturn(Optional.of(parcel));

        Parcel result = parcelService.processCourierDropOff(
            "TEST999", "STATION_01", "COMP_S1", "COURIER_01");

        verify(hardwarePortMock).openCompartment("STATION_01", "COMP_S1");
        verify(hardwarePortMock, never()).confirmDoorClosed(any(), any());
        assertThat(result.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(result.getPickupCodeHash()).isNull();
        assertThat(parcel.getAssignedCompartment().getStatus()).isEqualTo(CompartmentStatus.RESERVED);
        verify(parcelRepositoryMock, never()).save(any(Parcel.class));
        verifyNoInteractions(notificationPortMock);
    }

    @Test
    @DisplayName("confirmação de fecho transiciona para READY_FOR_PICKUP e emite código de recolha")
    void testConfirmDropOffWithClosedDoor() {
        Parcel parcel = parcelWithAssignedCompartment("TEST995", "user@example.com", "S", "COMP_S1", "STATION_01");
        Compartment compartment = parcel.getAssignedCompartment();
        when(parcelRepositoryMock.findByTrackingCode("TEST995")).thenReturn(Optional.of(parcel));
        when(parcelRepositoryMock.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hardwarePortMock.isDoorClosed("STATION_01", "COMP_S1")).thenReturn(true);

        Parcel result = parcelService.confirmCourierDropOff(
            "TEST995", "STATION_01", "COMP_S1", "COURIER_01");

        verify(hardwarePortMock).confirmDoorClosed("STATION_01", "COMP_S1");
        verify(hardwarePortMock).isDoorClosed("STATION_01", "COMP_S1");
        verify(compartmentRepositoryMock).save(compartment);
        assertThat(compartment.getStatus()).isEqualTo(CompartmentStatus.OCCUPIED);
        assertThat(result.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        assertThat(result.getPickupCodeHash()).isNotBlank();
        assertThat(result.getPickupDeadline()).isEqualTo(NOW.plusHours(24));

        ArgumentCaptor<String> pickupCode = ArgumentCaptor.forClass(String.class);
        verify(notificationPortMock).sendPickupCode(
            eq("user@example.com"), eq("TEST995"), pickupCode.capture());
        assertThat(PickupCode.matches(pickupCode.getValue(), result.getPickupCodeHash())).isTrue();
    }

    @Test
    @DisplayName("confirmação com porta ainda aberta não transiciona nem emite código")
    void testConfirmDropOffWithOpenDoor() {
        Parcel parcel = parcelWithAssignedCompartment("TEST994", "user@example.com", "S", "COMP_S1", "STATION_01");
        when(parcelRepositoryMock.findByTrackingCode("TEST994")).thenReturn(Optional.of(parcel));
        when(hardwarePortMock.isDoorClosed("STATION_01", "COMP_S1")).thenReturn(false);

        assertThatThrownBy(() ->
            parcelService.confirmCourierDropOff("TEST994", "STATION_01", "COMP_S1", "COURIER_01")
        ).isInstanceOf(HardwareException.class)
         .hasMessageContaining("não foi confirmada como fechada");

        verify(hardwarePortMock).confirmDoorClosed("STATION_01", "COMP_S1");
        verify(hardwarePortMock).isDoorClosed("STATION_01", "COMP_S1");
        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(parcel.getPickupCodeHash()).isNull();
        assertThat(parcel.getAssignedCompartment().getStatus()).isEqualTo(CompartmentStatus.RESERVED);
        verify(parcelRepositoryMock, never()).save(any(Parcel.class));
        verify(compartmentRepositoryMock, never()).save(any(Compartment.class));
        verifyNoInteractions(notificationPortMock);
    }

    @Test
    @DisplayName("falha ao confirmar fecho não transiciona nem emite código")
    void testConfirmDropOffHardwareCloseFailure() {
        Parcel parcel = parcelWithAssignedCompartment("TEST993", "user@example.com", "S", "COMP_S1", "STATION_01");
        when(parcelRepositoryMock.findByTrackingCode("TEST993")).thenReturn(Optional.of(parcel));
        doThrow(new HardwareException("hardware failure: failed to confirm closing"))
            .when(hardwarePortMock).confirmDoorClosed("STATION_01", "COMP_S1");

        assertThatThrownBy(() ->
            parcelService.confirmCourierDropOff("TEST993", "STATION_01", "COMP_S1", "COURIER_01")
        ).isInstanceOf(HardwareException.class)
         .hasMessageContaining("hardware failure");

        verify(hardwarePortMock, never()).isDoorClosed(any(), any());
        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(parcel.getPickupCodeHash()).isNull();
        assertThat(parcel.getAssignedCompartment().getStatus()).isEqualTo(CompartmentStatus.RESERVED);
        verify(parcelRepositoryMock, never()).save(any(Parcel.class));
        verify(compartmentRepositoryMock, never()).save(any(Compartment.class));
        verifyNoInteractions(notificationPortMock);
    }

    @Test
    @DisplayName("dropoff de encomenda já pronta é rejeitado sem interagir com hardware")
    void testDropOffAlreadyReadyParcelRejected() {
        Parcel parcel = new Parcel("TEST992", "user@example.com", "S");
        parcel.markAsReadyForPickup("COURIER_01");
        when(parcelRepositoryMock.findByTrackingCode("TEST992")).thenReturn(Optional.of(parcel));

        assertThatThrownBy(() ->
            parcelService.processCourierDropOff("TEST992", "STATION_01", "COMP_S1", "COURIER_01")
        ).isInstanceOf(InvalidTransitionException.class);

        verifyNoInteractions(hardwarePortMock);
    }

    @Test
    @DisplayName("confirmação de encomenda já pronta é rejeitada sem interagir com hardware")
    void testConfirmAlreadyReadyParcelRejected() {
        Parcel parcel = new Parcel("TEST991", "user@example.com", "S");
        parcel.markAsReadyForPickup("COURIER_01");
        when(parcelRepositoryMock.findByTrackingCode("TEST991")).thenReturn(Optional.of(parcel));

        assertThatThrownBy(() ->
            parcelService.confirmCourierDropOff("TEST991", "STATION_01", "COMP_S1", "COURIER_01")
        ).isInstanceOf(InvalidTransitionException.class);

        verifyNoInteractions(hardwarePortMock);
    }

    @Test
    @DisplayName("confirmação de encomenda inexistente lança EntityNotFoundException")
    void testConfirmDropOffUnknownParcel() {
        when(parcelRepositoryMock.findByTrackingCode("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            parcelService.confirmCourierDropOff("MISSING", "STATION_01", "COMP_S1", "COURIER_01")
        ).isInstanceOf(EntityNotFoundException.class)
         .hasMessageContaining("MISSING");

        verifyNoInteractions(hardwarePortMock);
    }

    @Test 
    @DisplayName("dropoff usa o tamanho do compartimento atribuído e rejeita quando é demasiado pequeno")
    void testDropOffSizeMismatch() {
        Parcel parcel = parcelWithAssignedCompartment(
            "TEST998", "user@example.com", "L", "COMP_S1", "S", "STATION_01");
        when(parcelRepositoryMock.findByTrackingCode("TEST998")).thenReturn(Optional.of(parcel));

        assertThatThrownBy(() ->
            parcelService.processCourierDropOff("TEST998", "STATION_01", "COMP_S1", "COURIER_01")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("muito pequeno");

        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(parcel.getPickupCodeHash()).isNull();
        assertThat(parcel.getAssignedCompartment().getStatus()).isEqualTo(CompartmentStatus.RESERVED);
        verifyNoInteractions(hardwarePortMock);
        verifyNoInteractions(notificationPortMock);
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
    @DisplayName("dropoff usa o tamanho do compartimento atribuído, não o texto do compartmentId")
    void testDropOffUsesAssignedCompartmentSize() {
        Parcel parcel = parcelWithAssignedCompartment(
            "TEST997", "user@example.com", "S", "COMP_123", "M", "STATION_01");
        when(parcelRepositoryMock.findByTrackingCode("TEST997")).thenReturn(Optional.of(parcel));

        Parcel result = parcelService.processCourierDropOff(
            "TEST997", "STATION_01", "COMP_123", "COURIER_01");

        assertThat(result.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(result.getAssignedCompartment().getStatus()).isEqualTo(CompartmentStatus.RESERVED);
        verify(hardwarePortMock).openCompartment("STATION_01", "COMP_123");
    }

    @Test
    @DisplayName("dropoff rejeita um compartimento diferente do atribuído")
    void testDropOffRejectsDifferentCompartment() {
        Parcel parcel = parcelWithAssignedCompartment("TEST988", "user@example.com", "S", "COMP_S1", "STATION_01");
        when(parcelRepositoryMock.findByTrackingCode("TEST988")).thenReturn(Optional.of(parcel));

        assertThatThrownBy(() ->
            parcelService.processCourierDropOff("TEST988", "STATION_01", "COMP_S2", "COURIER_01")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("não corresponde ao compartimento atribuído");

        verifyNoInteractions(hardwarePortMock);
        verifyNoInteractions(notificationPortMock);
    }

    @Test
    @DisplayName("confirmação rejeita um compartimento diferente do atribuído")
    void testConfirmDropOffRejectsDifferentCompartment() {
        Parcel parcel = parcelWithAssignedCompartment("TEST987", "user@example.com", "S", "COMP_S1", "STATION_01");
        when(parcelRepositoryMock.findByTrackingCode("TEST987")).thenReturn(Optional.of(parcel));

        assertThatThrownBy(() ->
            parcelService.confirmCourierDropOff("TEST987", "STATION_01", "COMP_S2", "COURIER_01")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("não corresponde ao compartimento atribuído");

        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(parcel.getAssignedCompartment().getStatus()).isEqualTo(CompartmentStatus.RESERVED);
        verifyNoInteractions(hardwarePortMock);
        verifyNoInteractions(notificationPortMock);
    }

    @Test
    @DisplayName("dropoff de encomenda sem compartimento atribuído é rejeitado")
    void testDropOffWithoutAssignedCompartment() {
        Parcel parcel = new Parcel("TEST986", "user@example.com", "S");
        when(parcelRepositoryMock.findByTrackingCode("TEST986")).thenReturn(Optional.of(parcel));

        assertThatThrownBy(() ->
            parcelService.processCourierDropOff("TEST986", "STATION_01", "COMP_S1", "COURIER_01")
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("não tem compartimento atribuído");

        verifyNoInteractions(hardwarePortMock);
        verifyNoInteractions(notificationPortMock);
    }

    @Test
    @DisplayName("confirmação sem compartimento atribuído é rejeitada")
    void testConfirmDropOffWithoutAssignedCompartment() {
        Parcel parcel = new Parcel("TEST985", "user@example.com", "S");
        when(parcelRepositoryMock.findByTrackingCode("TEST985")).thenReturn(Optional.of(parcel));

        assertThatThrownBy(() ->
            parcelService.confirmCourierDropOff("TEST985", "STATION_01", "COMP_S1", "COURIER_01")
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("não tem compartimento atribuído");

        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.CREATED);
        verifyNoInteractions(hardwarePortMock);
        verifyNoInteractions(notificationPortMock);
    }

    @Test
    @DisplayName("pickup por código abre o compartimento atribuído, transiciona e liberta-o")
    void testSuccessfulRecipientPickUp() {
        String pickupCode = "ABCD2345";
        Parcel parcel = parcelReadyForPickup(
            "TEST996", "user@example.com", "M", "COMP_M1", "STATION_01", pickupCode);
        Compartment compartment = parcel.getAssignedCompartment();
        when(parcelRepositoryMock.findByPickupCodeHash(PickupCode.hash(pickupCode)))
            .thenReturn(Optional.of(parcel));
        when(parcelRepositoryMock.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Parcel result = parcelService.processRecipientPickUp(" abcd2345 ");

        verify(hardwarePortMock).openCompartment("STATION_01", "COMP_M1");
        verify(compartmentRepositoryMock).save(compartment);
        assertThat(result.getCurrentState()).isEqualTo(ParcelState.PICKED_UP);
        assertThat(result.getPickupCodeHash()).isNull();
        assertThat(compartment.getStatus()).isEqualTo(CompartmentStatus.FREE);
        assertThat(result.getTransitions()).hasSize(3);
    }

    @Test
    @DisplayName("falha de hardware no pickup mantém a encomenda pronta e o código válido")
    void testHardwareFailureDuringPickUp() {
        String pickupCode = "EFGH2345";
        Parcel parcel = parcelReadyForPickup(
            "TEST995", "user@example.com", "S", "COMP_S1", "STATION_01", pickupCode);
        when(parcelRepositoryMock.findByPickupCodeHash(PickupCode.hash(pickupCode)))
            .thenReturn(Optional.of(parcel));
        doThrow(new HardwareException("hardware failure"))
            .when(hardwarePortMock).openCompartment("STATION_01", "COMP_S1");

        assertThatThrownBy(() -> parcelService.processRecipientPickUp(pickupCode))
            .isInstanceOf(HardwareException.class);

        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        assertThat(parcel.getPickupCodeHash()).isNotBlank();
        assertThat(parcel.getAssignedCompartment().getStatus()).isEqualTo(CompartmentStatus.OCCUPIED);
        verify(parcelRepositoryMock, never()).save(any(Parcel.class));
        verify(compartmentRepositoryMock, never()).save(any(Compartment.class));
    }

    @Test
    @DisplayName("pickup com código de formato inválido é rejeitado sem consultar a base de dados")
    void testPickupWithInvalidFormat() {
        assertThatThrownBy(() -> parcelService.processRecipientPickUp("ABC"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("código de recolha inválido");

        verifyNoInteractions(parcelRepositoryMock, hardwarePortMock);
    }

    @Test
    @DisplayName("pickup com código desconhecido é rejeitado sem interagir com hardware")
    void testPickupWithUnknownCode() {
        String pickupCode = "ABCD2345";
        when(parcelRepositoryMock.findByPickupCodeHash(PickupCode.hash(pickupCode)))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> parcelService.processRecipientPickUp(pickupCode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("código de recolha inválido");

        verifyNoInteractions(hardwarePortMock);
    }

    @Test
    @DisplayName("pickup rejeita hash que não corresponde ao código indicado")
    void testPickupWithMismatchedHash() {
        String pickupCode = "ABCD2345";
        Parcel parcel = parcelReadyForPickup(
            "TEST985", "user@example.com", "S", "COMP_S1", "STATION_01", "ZZZZ9999");
        when(parcelRepositoryMock.findByPickupCodeHash(PickupCode.hash(pickupCode)))
            .thenReturn(Optional.of(parcel));

        assertThatThrownBy(() -> parcelService.processRecipientPickUp(pickupCode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("código de recolha inválido");

        verifyNoInteractions(hardwarePortMock);
    }

    @Test
    @DisplayName("código de uma encomenda expirada não abre o compartimento")
    void testPickupWithExpiredParcel() {
        String pickupCode = "ABCD2345";
        Parcel parcel = parcelReadyForPickup(
            "TEST984", "user@example.com", "S", "COMP_S1", "STATION_01", pickupCode);
        parcel.expire("SYSTEM");
        when(parcelRepositoryMock.findByPickupCodeHash(PickupCode.hash(pickupCode)))
            .thenReturn(Optional.of(parcel));

        assertThatThrownBy(() -> parcelService.processRecipientPickUp(pickupCode))
            .isInstanceOf(InvalidTransitionException.class)
            .hasMessageContaining("EXPIRED");

        verifyNoInteractions(hardwarePortMock);
        assertThat(parcel.getAssignedCompartment().getStatus()).isEqualTo(CompartmentStatus.OCCUPIED);
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
    @DisplayName("drop-off depois do prazo é rejeitado sem abrir o compartimento")
    void testDropOffAfterDeadlineRejected() {
        Parcel parcel = parcelWithAssignedCompartment(
            "TEST988", "user@example.com", "S", "COMP_S1", "STATION_01");
        parcel.setDropOffDeadline(NOW.minusSeconds(1));
        when(parcelRepositoryMock.findByTrackingCode("TEST988")).thenReturn(Optional.of(parcel));

        assertThatThrownBy(() ->
            parcelService.processCourierDropOff("TEST988", "STATION_01", "COMP_S1", "COURIER_01")
        ).isInstanceOf(InvalidTransitionException.class)
         .hasMessageContaining("prazo de drop-off");

        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(parcel.getAssignedCompartment().getStatus()).isEqualTo(CompartmentStatus.RESERVED);
        verifyNoInteractions(hardwarePortMock, notificationPortMock);
    }

    @Test
    @DisplayName("drop-off exatamente no prazo ainda é aceite")
    void testDropOffAtDeadlineAccepted() {
        Parcel parcel = parcelWithAssignedCompartment(
            "TEST987", "user@example.com", "S", "COMP_S1", "STATION_01");
        parcel.setDropOffDeadline(NOW);
        when(parcelRepositoryMock.findByTrackingCode("TEST987")).thenReturn(Optional.of(parcel));

        Parcel result = parcelService.processCourierDropOff(
            "TEST987", "STATION_01", "COMP_S1", "COURIER_01");

        assertThat(result.getCurrentState()).isEqualTo(ParcelState.CREATED);
        verify(hardwarePortMock).openCompartment("STATION_01", "COMP_S1");
    }

    @Test
    @DisplayName("pickup depois do prazo é rejeitado sem abrir o compartimento")
    void testPickupAfterDeadlineRejected() {
        String pickupCode = "ABCD2345";
        Parcel parcel = parcelReadyForPickup(
            "TEST986", "user@example.com", "S", "COMP_S1", "STATION_01", pickupCode);
        parcel.setPickupDeadline(NOW.minusSeconds(1));
        when(parcelRepositoryMock.findByPickupCodeHash(PickupCode.hash(pickupCode)))
            .thenReturn(Optional.of(parcel));

        assertThatThrownBy(() -> parcelService.processRecipientPickUp(pickupCode))
            .isInstanceOf(InvalidTransitionException.class)
            .hasMessageContaining("prazo de recolha");

        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        assertThat(parcel.getAssignedCompartment().getStatus()).isEqualTo(CompartmentStatus.OCCUPIED);
        verifyNoInteractions(hardwarePortMock);
    }

    @Test
    @DisplayName("pickup exatamente no prazo ainda é aceite")
    void testPickupAtDeadlineAccepted() {
        String pickupCode = "EFGH2345";
        Parcel parcel = parcelReadyForPickup(
            "TEST985", "user@example.com", "S", "COMP_S1", "STATION_01", pickupCode);
        parcel.setPickupDeadline(NOW);
        when(parcelRepositoryMock.findByPickupCodeHash(PickupCode.hash(pickupCode)))
            .thenReturn(Optional.of(parcel));
        when(parcelRepositoryMock.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Parcel result = parcelService.processRecipientPickUp(pickupCode);

        assertThat(result.getCurrentState()).isEqualTo(ParcelState.PICKED_UP);
        verify(hardwarePortMock).openCompartment("STATION_01", "COMP_S1");
    }

    @Test
    @DisplayName("aplicação de deadlines cancela encomendas e liberta compartimentos")
    void testApplyDeadlinesCancelsParcelAndReleasesCompartment() {
        Parcel parcel = parcelWithAssignedCompartment(
            "TEST984", "user@example.com", "S", "COMP_S1", "STATION_01");
        parcel.setDropOffDeadline(NOW.minusSeconds(1));
        Compartment compartment = parcel.getAssignedCompartment();
        when(parcelRepositoryMock.findByCurrentStateAndDropOffDeadlineBefore(ParcelState.CREATED, NOW))
            .thenReturn(List.of(parcel));
        when(parcelRepositoryMock.findByCurrentStateAndPickupDeadlineBefore(ParcelState.READY_FOR_PICKUP, NOW))
            .thenReturn(List.of());
        when(parcelRepositoryMock.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        parcelService.applyDeadlines();

        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.CANCELLED);
        assertThat(compartment.getStatus()).isEqualTo(CompartmentStatus.FREE);
        verify(compartmentRepositoryMock).save(compartment);
        verify(parcelRepositoryMock).save(parcel);
    }

    @Test
    @DisplayName("aplicação de deadlines expira encomendas e mantém o compartimento ocupado")
    void testApplyDeadlinesExpiresParcelAndKeepsCompartmentOccupied() {
        String pickupCode = "IJKL2345";
        Parcel parcel = parcelReadyForPickup(
            "TEST983", "user@example.com", "S", "COMP_S1", "STATION_01", pickupCode);
        parcel.setPickupDeadline(NOW.minusSeconds(1));
        Compartment compartment = parcel.getAssignedCompartment();
        when(parcelRepositoryMock.findByCurrentStateAndDropOffDeadlineBefore(ParcelState.CREATED, NOW))
            .thenReturn(List.of());
        when(parcelRepositoryMock.findByCurrentStateAndPickupDeadlineBefore(ParcelState.READY_FOR_PICKUP, NOW))
            .thenReturn(List.of(parcel));
        when(parcelRepositoryMock.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        parcelService.applyDeadlines();

        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.EXPIRED);
        assertThat(compartment.getStatus()).isEqualTo(CompartmentStatus.OCCUPIED);
        verify(parcelRepositoryMock).save(parcel);
        verifyNoInteractions(compartmentRepositoryMock);
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

        ArgumentCaptor<Parcel> parcelCaptor = ArgumentCaptor.forClass(Parcel.class);
        verify(parcelRepositoryMock).save(parcelCaptor.capture());
        assertThat(parcelCaptor.getValue().getRegisteredAt()).isEqualTo(NOW);
        assertThat(parcelCaptor.getValue().getDropOffDeadline()).isEqualTo(NOW.plusHours(2));
    }

    private ParcelRequestDto buildRegisterRequest(String stationId, String size) {
        ParcelRequestDto request = new ParcelRequestDto();
        request.setStationId(stationId);
        request.setCompartmentSize(size);
        request.setRecipientContact("destinatario@example.com");
        request.setPartnerId("PARTNER_01");
        return request;
    }

    private Parcel parcelWithAssignedCompartment(
            String trackingCode, String contact, String parcelSize,
            String compartmentCode, String stationCode) {
        return parcelWithAssignedCompartment(
            trackingCode, contact, parcelSize, compartmentCode, parcelSize, stationCode);
    }

    private Parcel parcelWithAssignedCompartment(
            String trackingCode, String contact, String parcelSize,
            String compartmentCode, String compartmentSize, String stationCode) {
        Station station = new Station(stationCode, "Estação " + stationCode, 40.63, -8.65);
        Compartment compartment = new Compartment(
            compartmentCode, station, CompartmentSize.valueOf(compartmentSize));
        compartment.reserve();
        Parcel parcel = new Parcel(trackingCode, contact, parcelSize);
        parcel.setStation(station);
        parcel.setAssignedCompartment(compartment);
        return parcel;
    }

    private Parcel parcelReadyForPickup(
            String trackingCode, String contact, String size,
            String compartmentCode, String stationCode, String pickupCode) {
        Parcel parcel = parcelWithAssignedCompartment(
            trackingCode, contact, size, compartmentCode, stationCode);
        parcel.getAssignedCompartment().occupy();
        parcel.markAsReadyForPickup("COURIER_01");
        parcel.issuePickupCode(PickupCode.hash(pickupCode));
        return parcel;
    }
}
