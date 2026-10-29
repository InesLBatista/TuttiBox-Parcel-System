package tuttibox.hw1.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import tuttibox.hw1.TestcontainersConfiguration;
import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.CompartmentStatus;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelState;
import tuttibox.hw1.model.Station;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.ParcelRepository;
import tuttibox.hw1.repository.StationRepository;

@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class ParcelDeadlineIntegrationTest {
    @Autowired
    private ParcelService parcelService;
    @Autowired
    private ParcelRepository parcelRepository;
    @Autowired
    private CompartmentRepository compartmentRepository;
    @Autowired
    private StationRepository stationRepository;

    private Station station;

    @BeforeEach
    void setUp() {
        parcelRepository.deleteAll();
        compartmentRepository.deleteAll();
        stationRepository.deleteAll();
        station = stationRepository.save(new Station("ST-DEADLINE", "Estação Deadlines", 40.63, -8.65));
    }

    @Test
    @DisplayName("aplicação de deadlines cancela encomendas cujo drop-off expirou")
    void testApplyDeadlinesCancelsExpiredDropOff() {
        Compartment compartment = reservedCompartment("C-S1");
        Parcel parcel = new Parcel("TRK-DEADLINE-01", "client@example.com", "S");
        parcel.setStation(station);
        parcel.setAssignedCompartment(compartment);
        parcel.setRegisteredAt(LocalDateTime.now().minusDays(2));
        parcel.setDropOffDeadline(LocalDateTime.now().minusMinutes(1));
        parcelRepository.save(parcel);

        parcelService.applyDeadlines();

        Parcel updated = parcelRepository.findByTrackingCode("TRK-DEADLINE-01").orElseThrow();
        assertThat(updated.getCurrentState()).isEqualTo(ParcelState.CANCELLED);
        assertThat(updated.getAssignedCompartment().getStatus()).isEqualTo(CompartmentStatus.FREE);
        assertThat(updated.getTransitions())
            .anySatisfy(transition -> {
                assertThat(transition.getToState()).isEqualTo(ParcelState.CANCELLED);
                assertThat(transition.getActor()).isEqualTo("SYSTEM");
            });
    }

    @Test
    @DisplayName("aplicação de deadlines expira encomendas cujo pickup expirou")
    void testApplyDeadlinesExpiresExpiredPickup() {
        Compartment compartment = occupiedCompartment("C-M1");
        Parcel parcel = new Parcel("TRK-DEADLINE-02", "client@example.com", "M");
        parcel.setStation(station);
        parcel.setAssignedCompartment(compartment);
        parcel.markAsReadyForPickup("COURIER_01", LocalDateTime.now().minusMinutes(1));
        parcelRepository.save(parcel);

        parcelService.applyDeadlines();

        Parcel updated = parcelRepository.findByTrackingCode("TRK-DEADLINE-02").orElseThrow();
        assertThat(updated.getCurrentState()).isEqualTo(ParcelState.EXPIRED);
        assertThat(updated.getAssignedCompartment().getStatus()).isEqualTo(CompartmentStatus.OCCUPIED);
        assertThat(updated.getTransitions())
            .anySatisfy(transition -> {
                assertThat(transition.getToState()).isEqualTo(ParcelState.EXPIRED);
                assertThat(transition.getActor()).isEqualTo("SYSTEM");
            });
    }

    @Test
    @DisplayName("aplicação de deadlines não altera encomendas dentro do prazo")
    void testApplyDeadlinesKeepsParcelsWithinDeadline() {
        Compartment compartment = reservedCompartment("C-L1");
        Parcel parcel = new Parcel("TRK-DEADLINE-03", "client@example.com", "L");
        parcel.setStation(station);
        parcel.setAssignedCompartment(compartment);
        parcel.setDropOffDeadline(LocalDateTime.now().plusHours(1));
        parcelRepository.save(parcel);

        parcelService.applyDeadlines();

        Parcel updated = parcelRepository.findByTrackingCode("TRK-DEADLINE-03").orElseThrow();
        assertThat(updated.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(updated.getAssignedCompartment().getStatus()).isEqualTo(CompartmentStatus.RESERVED);
    }

    private Compartment reservedCompartment(String code) {
        Compartment compartment = new Compartment(code, station, CompartmentSize.S);
        compartment.reserve();
        return compartmentRepository.save(compartment);
    }

    private Compartment occupiedCompartment(String code) {
        Compartment compartment = new Compartment(code, station, CompartmentSize.M);
        compartment.reserve();
        compartment.occupy();
        return compartmentRepository.save(compartment);
    }
}
