package tuttibox.hw1.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import tuttibox.hw1.TestcontainersConfiguration;
import tuttibox.hw1.dto.ParcelRequestDto;
import tuttibox.hw1.exception.NoAvailableCompartmentException;
import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.CompartmentStatus;
import tuttibox.hw1.model.Station;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.ParcelRepository;
import tuttibox.hw1.repository.StationRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ParcelAllocationConcurrencyIntegrationTest {
    @Autowired
    private ParcelService parcelService;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private CompartmentRepository compartmentRepository;

    @Autowired
    private ParcelRepository parcelRepository;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        Station station = stationRepository.save(
            new Station("ST-CONC", "Estação Concorrência", 40.6305, -8.6571));
        compartmentRepository.save(new Compartment("C-S1", station, CompartmentSize.S));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }


    //teste importante, estava a ter erros quando estava a fazer simulações, vem daqui agora a confirmação
    @Test
    @DisplayName("dois pedidos simultâneos não reservam o mesmo último compartimento")
    void testConcurrentRequestsDoNotShareLastCompartment() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<RegistrationResult> first = executor.submit(
                () -> register("PARTNER_A", ready, start));
            Future<RegistrationResult> second = executor.submit(
                () -> register("PARTNER_B", ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            RegistrationResult resultA = first.get(15, TimeUnit.SECONDS);
            RegistrationResult resultB = second.get(15, TimeUnit.SECONDS);

            List<RegistrationResult> results = List.of(resultA, resultB);
            List<RegistrationResult> successful = results.stream()
                .filter(result -> java.util.Objects.requireNonNull(result).succeeded())
                .toList();
            List<RegistrationResult> failed = results.stream()
                .filter(result -> !result.succeeded())
                .toList();

            assertThat(successful).hasSize(1);
            assertThat(failed).hasSize(1);
            assertThat(failed.get(0).failure())
                .isInstanceOf(NoAvailableCompartmentException.class);

            assertThat(parcelRepository.count()).isEqualTo(1);
            Compartment compartment = compartmentRepository.findAll().get(0);
            assertThat(compartment.getStatus()).isEqualTo(CompartmentStatus.RESERVED);
        } finally {
            executor.shutdownNow();
        }
    }

    private RegistrationResult register(
        String partnerId, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);

        try {
            ParcelRequestDto request = new ParcelRequestDto();
            request.setStationId("ST-CONC");
            request.setCompartmentSize("S");
            request.setRecipientContact(partnerId.toLowerCase() + "@example.com");
            request.setPartnerId(partnerId);

            parcelService.registerParcel(request);
            return new RegistrationResult(null);
        } catch (Throwable failure) {
            return new RegistrationResult(failure);
        }
    }

    private void cleanDatabase() {
        parcelRepository.deleteAll();
        compartmentRepository.deleteAll();
        stationRepository.deleteAll();
    }

    private record RegistrationResult(Throwable failure) {
        boolean succeeded() {
            return failure == null;
        }
    }
}
