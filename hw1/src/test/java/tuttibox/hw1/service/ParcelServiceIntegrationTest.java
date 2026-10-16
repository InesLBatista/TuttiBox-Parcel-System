package tuttibox.hw1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tuttibox.hw1.fake.InMemoryHardwareFake;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelState;
import tuttibox.hw1.port.HardwareException;

@SpringBootTest 
class ParcelServiceIntegrationTest {
    @Autowired 
    private ParcelService parcelService;

    @Autowired 
    private InMemoryHardwareFake hardwareFake;

    @BeforeEach 
    void setUp() {
        //resetar estado fake antes de cada teste
        hardwareFake.setInjectFailure(false);
    }

    @Test 
    @DisplayName ("integration of successul drof off using the in-memory hardware fake")
    void testSuccessulDropOffWithFake() {
        Parcel parcel = new Parcel("TRK-INT-01", "client@example.com", "M");
        parcelService.dropOffParcel(parcel, "STATION_A", "COMP_1", "COURIER_A");
        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        assertThat(hardwareFake.isDoorClosed("STATION_1", "COMP_A"));
    }

    @Test 
    @DisplayName ("integration of hardware failure injections prevents state transition during drop off")
    void testHardwareFailureInjectionDuringDropOff() {
        Parcel parcel = new Parcel("TRK-INT-02", "client@example.com", "S");
        //injeção da falha no hardware
        hardwareFake.setInjectFailure(true);
        assertThatThrownBy(() -> 
            parcelService.dropOffParcel(parcel, "STATION_A", "COMP_2", "COURIER_A")
        )
        .isInstanceOf(HardwareException.class)
        .hasMessageContaining("hardware failure");

        //garantia de como hardware falhou, a ecomenda deve manter o seu estado
        assertThat(parcel.getCurrentState()).isEqualTo(ParcelState.CREATED);
    }
}
