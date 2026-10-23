package tuttibox.hw1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import tuttibox.hw1.TestcontainersConfiguration;
import tuttibox.hw1.fake.InMemoryHardwareFake;
import tuttibox.hw1.fake.InMemoryNotificationFake;
import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.CompartmentStatus;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelState;
import tuttibox.hw1.model.Station;
import tuttibox.hw1.port.HardwareException;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.ParcelRepository;
import tuttibox.hw1.repository.StationRepository;

@SpringBootTest 
@Import(TestcontainersConfiguration.class)
@Transactional
class ParcelServiceIntegrationTest {
    @Autowired 
    private ParcelService parcelService;

    @Autowired 
    private InMemoryHardwareFake hardwareFake;

    @Autowired 
    private ParcelRepository parcelRepository;

    @Autowired
    private InMemoryNotificationFake notificationFake;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private CompartmentRepository compartmentRepository;

    @BeforeEach
    void setUp() {
        hardwareFake.setInjectFailure(false);
        hardwareFake.setInjectCloseFailure(false);
        hardwareFake.setInjectDoorNotClosed(false);
        notificationFake.clear();
        parcelRepository.deleteAll();
    }

    @Test 
    @DisplayName("dropoff abre a porta e só a confirmação de fecho transiciona para READY_FOR_PICKUP")
    void testSuccessfulDropOffWithFake() {
        Parcel saved = saveParcelWithCompartment(
            "TRK-INT-01", "client@example.com", "M", "COMP_M1", "STATION_A");

        Parcel opened = parcelService.processCourierDropOff(
            saved.getTrackingCode(), "STATION_A", "COMP_M1", "COURIER_A");

        assertThat(opened.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(opened.getPickupCodeHash()).isNull();
        assertThat(compartmentRepository.findByStationCodeAndCode("STATION_A", "COMP_M1")
                .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.RESERVED);
        assertThat(hardwareFake.isDoorClosed("STATION_A", "COMP_M1")).isFalse();
        assertThat(notificationFake.getNotifications()).isEmpty();

        Parcel result = parcelService.confirmCourierDropOff(
            saved.getTrackingCode(), "STATION_A", "COMP_M1", "COURIER_A");

        assertThat(result.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        assertThat(result.getPickupCodeHash()).isNotBlank();
        assertThat(compartmentRepository.findByStationCodeAndCode("STATION_A", "COMP_M1")
                .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.OCCUPIED);
        assertThat(hardwareFake.isDoorClosed("STATION_A", "COMP_M1")).isTrue();

        InMemoryNotificationFake.Notification notification = notificationFake
            .findLastByTrackingCode("TRK-INT-01")
            .orElseThrow();
        assertThat(PickupCode.matches(notification.pickupCode(), result.getPickupCodeHash()))
            .isTrue();
    }

    @Test 
    @DisplayName("falha de abertura impede transição, estado fica em CREATED")
    void testHardwareFailureInjectionDuringDropOff() {
        Parcel saved = saveParcelWithCompartment(
            "TRK-INT-02", "client@example.com", "S", "COMP_S1", "STATION_A");
        hardwareFake.setInjectFailure(true);

        assertThatThrownBy(() ->
            parcelService.processCourierDropOff(
                saved.getTrackingCode(), "STATION_A", "COMP_S1", "COURIER_A")
        )
        .isInstanceOf(HardwareException.class)
        .hasMessageContaining("hardware failure");

        Parcel unchanged = parcelRepository.findByTrackingCode("TRK-INT-02").orElseThrow();
        assertThat(unchanged.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(unchanged.getPickupCodeHash()).isNull();
        assertThat(compartmentRepository.findByStationCodeAndCode("STATION_A", "COMP_S1")
                .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.RESERVED);
        assertThat(notificationFake.getNotifications()).isEmpty();
    }

    @Test
    @DisplayName("falha de hardware na confirmação de fecho mantém estado CREATED")
    void testHardwareCloseFailureInjectionDuringConfirmation() {
        Parcel saved = saveParcelWithCompartment(
            "TRK-INT-03", "client@example.com", "S", "COMP_S1", "STATION_A");
        parcelService.processCourierDropOff(
            saved.getTrackingCode(), "STATION_A", "COMP_S1", "COURIER_A");
        hardwareFake.setInjectCloseFailure(true);

        assertThatThrownBy(() ->
            parcelService.confirmCourierDropOff(
                saved.getTrackingCode(), "STATION_A", "COMP_S1", "COURIER_A")
        )
        .isInstanceOf(HardwareException.class)
        .hasMessageContaining("failed to confirm closing");

        Parcel unchanged = parcelRepository.findByTrackingCode("TRK-INT-03").orElseThrow();
        assertThat(unchanged.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(unchanged.getPickupCodeHash()).isNull();
        assertThat(compartmentRepository.findByStationCodeAndCode("STATION_A", "COMP_S1")
                .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.RESERVED);
        assertThat(hardwareFake.isDoorClosed("STATION_A", "COMP_S1")).isFalse();
        assertThat(notificationFake.getNotifications()).isEmpty();
    }

    @Test
    @DisplayName("porta não confirmada como fechada mantém estado CREATED")
    void testDoorNotClosedDuringConfirmation() {
        Parcel saved = saveParcelWithCompartment(
            "TRK-INT-04", "client@example.com", "S", "COMP_S1", "STATION_A");
        parcelService.processCourierDropOff(
            saved.getTrackingCode(), "STATION_A", "COMP_S1", "COURIER_A");
        hardwareFake.setInjectDoorNotClosed(true);

        assertThatThrownBy(() ->
            parcelService.confirmCourierDropOff(
                saved.getTrackingCode(), "STATION_A", "COMP_S1", "COURIER_A")
        )
        .isInstanceOf(HardwareException.class)
        .hasMessageContaining("não foi confirmada como fechada");

        Parcel unchanged = parcelRepository.findByTrackingCode("TRK-INT-04").orElseThrow();
        assertThat(unchanged.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(unchanged.getPickupCodeHash()).isNull();
        assertThat(compartmentRepository.findByStationCodeAndCode("STATION_A", "COMP_S1")
                .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.RESERVED);
        assertThat(hardwareFake.isDoorClosed("STATION_A", "COMP_S1")).isFalse();
        assertThat(notificationFake.getNotifications()).isEmpty();
    }

    private Parcel saveParcelWithCompartment(
            String trackingCode, String contact, String size, String compartmentCode, String stationCode) {
        Station station = stationRepository.save(
            new Station(stationCode, "Estação " + stationCode, 40.63, -8.65));
        Compartment compartment = new Compartment(compartmentCode, station, CompartmentSize.valueOf(size));
        compartment.reserve();
        compartmentRepository.save(compartment);
        Parcel parcel = new Parcel(trackingCode, contact, size);
        parcel.setStation(station);
        parcel.setAssignedCompartment(compartment);
        return parcelRepository.save(parcel);
    }
}
