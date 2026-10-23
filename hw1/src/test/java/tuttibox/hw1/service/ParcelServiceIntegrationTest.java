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
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelState;
import tuttibox.hw1.port.HardwareException;
import tuttibox.hw1.repository.ParcelRepository;

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

    @BeforeEach
    void setUp() {
        hardwareFake.setInjectFailure(false);
        notificationFake.clear();
        parcelRepository.deleteAll();
    }

    @Test 
    @DisplayName("dropoff com hardware fake bem sucedido transiciona para READY_FOR_PICKUP")
    void testSuccessfulDropOffWithFake() {
        Parcel saved = parcelRepository.save(new Parcel("TRK-INT-01", "client@example.com", "M"));

        Parcel result = parcelService.processCourierDropOff(
            saved.getTrackingCode(), "STATION_A", "COMP_M1", "COURIER_A");

        assertThat(result.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        assertThat(result.getPickupCodeHash()).isNotBlank();
        assertThat(hardwareFake.isDoorClosed("STATION_A", "COMP_M1")).isFalse();

        InMemoryNotificationFake.Notification notification = notificationFake
            .findLastByTrackingCode("TRK-INT-01")
            .orElseThrow();
        assertThat(PickupCode.matches(notification.pickupCode(), result.getPickupCodeHash()))
            .isTrue();
    }

    @Test 
    @DisplayName("falha de hardware impede transição, estado fica em CREATED")
    void testHardwareFailureInjectionDuringDropOff() {
        Parcel saved = parcelRepository.save(new Parcel("TRK-INT-02", "client@example.com", "S"));
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
        assertThat(notificationFake.getNotifications()).isEmpty();
    }
}
