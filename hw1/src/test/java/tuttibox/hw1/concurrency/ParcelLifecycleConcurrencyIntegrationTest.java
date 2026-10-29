package tuttibox.hw1.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
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
import tuttibox.hw1.dto.ParcelResponseDto;
import tuttibox.hw1.exception.NoAvailableCompartmentException;
import tuttibox.hw1.fake.InMemoryHardwareFake;
import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.CompartmentStatus;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelState;
import tuttibox.hw1.model.Station;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.ParcelRepository;
import tuttibox.hw1.repository.StationRepository;
import tuttibox.hw1.service.ParcelService;
import tuttibox.hw1.service.PickupCode;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ParcelLifecycleConcurrencyIntegrationTest {

    @Autowired
    private ParcelService parcelService;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private CompartmentRepository compartmentRepository;

    @Autowired
    private ParcelRepository parcelRepository;

    @Autowired
    private InMemoryHardwareFake hardwareFake;

    private Station station;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        station = stationRepository.save(new Station("ST-CONC-LIFECYCLE", "Estação Concorrência Lifecycle", 40.63, -8.65));
        hardwareFake.setInjectFailure(false);
        hardwareFake.setInjectCloseFailure(false);
        hardwareFake.setInjectDoorNotClosed(false);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Alta contenção: 10 pedidos concorrentes para 3 compartimentos alocam exatamente 3 e rejeitam 7")
    void testHighContentionConcurrentRegistration() throws Exception {
        compartmentRepository.save(new Compartment("C-1", station, CompartmentSize.S));
        compartmentRepository.save(new Compartment("C-2", station, CompartmentSize.M));
        compartmentRepository.save(new Compartment("C-3", station, CompartmentSize.L));

        int threadCount = 10;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        List<Future<RegistrationResult>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final String partnerId = "PARTNER_" + i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                try {
                    ParcelRequestDto dto = new ParcelRequestDto();
                    dto.setStationId(station.getCode());
                    dto.setCompartmentSize("S");
                    dto.setRecipientContact(partnerId.toLowerCase() + "@example.com");
                    dto.setPartnerId(partnerId);

                    ParcelResponseDto resp = parcelService.registerParcel(dto);
                    return new RegistrationResult(resp, null);
                } catch (Throwable t) {
                    return new RegistrationResult(null, t);
                }
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<RegistrationResult> results = new ArrayList<>();
        for (Future<RegistrationResult> f : futures) {
            results.add(f.get(15, TimeUnit.SECONDS));
        }
        executor.shutdownNow();

        List<RegistrationResult> successful = results.stream()
            .filter(result -> java.util.Objects.requireNonNull(result).succeeded())
            .toList();
        List<RegistrationResult> failed = results.stream().filter(r -> !r.succeeded()).toList();

        assertThat(successful).hasSize(3);
        assertThat(failed).hasSize(7);
        assertThat(failed).allSatisfy(r ->
            assertThat(r.failure()).isInstanceOf(NoAvailableCompartmentException.class));

        // Todos os compartimentos atribuídos têm de ser distintos (sem double booking)
        List<String> assignedCompartments = successful.stream()
            .map(r -> r.response().assignedCompartmentCode())
            .toList();
        assertThat(assignedCompartments).doesNotHaveDuplicates().hasSize(3);

        // Verificação dos invariantes na base de dados
        assertThat(parcelRepository.count()).isEqualTo(3);
        List<Compartment> compartments = compartmentRepository.findByStation_CodeOrderByCodeAsc(station.getCode());
        assertThat(compartments).allSatisfy(c ->
            assertThat(c.getStatus()).isEqualTo(CompartmentStatus.RESERVED));
    }

    @Test
    @DisplayName("Concorrência no levantamento: 2 pedidos simultâneos com o mesmo código de recolha executam apenas 1 levantamento")
    void testConcurrentPickupReplayAttack() throws Exception {
        compartmentRepository.save(new Compartment("C-PICK", station, CompartmentSize.M));

        // Registar e depositar encomenda
        ParcelRequestDto request = new ParcelRequestDto();
        request.setStationId(station.getCode());
        request.setCompartmentSize("M");
        request.setRecipientContact("destinatario@example.com");
        request.setPartnerId("LOJA_A");
        ParcelResponseDto registered = parcelService.registerParcel(request);

        parcelService.processCourierDropOff(registered.trackingCode(), station.getCode(), "C-PICK", "COURIER_1");
        parcelService.confirmCourierDropOff(registered.trackingCode(), station.getCode(), "C-PICK", "COURIER_1");

        // Recuperar hash do código de recolha gerado
        Parcel parcel = parcelRepository.findByTrackingCode(registered.trackingCode()).orElseThrow();
        // Gerar e atribuir código de teste determinístico para recolha
        String rawCode = PickupCode.generate();
        parcel.setPickupCodeHash(PickupCode.hash(rawCode));
        parcelRepository.save(parcel);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        List<Future<PickupResult>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                try {
                    Parcel p = parcelService.processRecipientPickUp(rawCode);
                    return new PickupResult(p, null);
                } catch (Throwable t) {
                    return new PickupResult(null, t);
                }
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        PickupResult r1 = futures.get(0).get(15, TimeUnit.SECONDS);
        PickupResult r2 = futures.get(1).get(15, TimeUnit.SECONDS);
        executor.shutdownNow();

        List<PickupResult> results = List.of(r1, r2);
        long successCount = results.stream()
            .filter(result -> java.util.Objects.requireNonNull(result).succeeded())
            .count();
        long failCount = results.stream().filter(r -> !r.succeeded()).count();

        assertThat(successCount).isEqualTo(1);
        assertThat(failCount).isEqualTo(1);

        // O compartimento deve estar libertado (FREE)
        Compartment updatedComp = compartmentRepository.findByStationCodeAndCode(station.getCode(), "C-PICK").orElseThrow();
        assertThat(updatedComp.getStatus()).isEqualTo(CompartmentStatus.FREE);

        // A encomenda deve estar no estado PICKED_UP e com código limpo
        Parcel updatedParcel = parcelRepository.findByTrackingCode(registered.trackingCode()).orElseThrow();
        assertThat(updatedParcel.getCurrentState()).isEqualTo(ParcelState.PICKED_UP);
        assertThat(updatedParcel.getPickupCodeHash()).isNull();
    }

    @Test
    @DisplayName("Concorrência: corrida entre cancelamento pelo parceiro e confirmação de depósito pelo courier")
    void testConcurrentDropOffAndCancellationRaceCondition() throws Exception {
        compartmentRepository.save(new Compartment("C-RACE", station, CompartmentSize.M));

        ParcelRequestDto request = new ParcelRequestDto();
        request.setStationId(station.getCode());
        request.setCompartmentSize("M");
        request.setRecipientContact("cliente@example.com");
        request.setPartnerId("LOJA_B");
        ParcelResponseDto reg = parcelService.registerParcel(request);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<OperationResult> dropOffFuture = executor.submit(() -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try {
                parcelService.processCourierDropOff(reg.trackingCode(), station.getCode(), "C-RACE", "COURIER_X");
                Parcel p = parcelService.confirmCourierDropOff(reg.trackingCode(), station.getCode(), "C-RACE", "COURIER_X");
                return new OperationResult("DROPOFF", p, null);
            } catch (Throwable t) {
                return new OperationResult("DROPOFF", null, t);
            }
        });

        Future<OperationResult> cancelFuture = executor.submit(() -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            try {
                Parcel p = parcelService.processParcelCancellation(reg.trackingCode(), "PARTNER");
                return new OperationResult("CANCEL", p, null);
            } catch (Throwable t) {
                return new OperationResult("CANCEL", null, t);
            }
        });

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        OperationResult dropOffResult = dropOffFuture.get(15, TimeUnit.SECONDS);
        OperationResult cancelResult = cancelFuture.get(15, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Exatamente uma das operações deve ter vencido
        boolean dropOffWon = dropOffResult.succeeded();
        boolean cancelWon = cancelResult.succeeded();
        assertThat(dropOffWon ^ cancelWon).as("Exatamente uma das operações deve ter sucesso").isTrue();

        Parcel finalParcel = parcelRepository.findByTrackingCode(reg.trackingCode()).orElseThrow();
        Compartment finalComp = compartmentRepository.findByStationCodeAndCode(station.getCode(), "C-RACE").orElseThrow();

        if (dropOffWon) {
            assertThat(finalParcel.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
            assertThat(finalComp.getStatus()).isEqualTo(CompartmentStatus.OCCUPIED);
        } else {
            assertThat(finalParcel.getCurrentState()).isEqualTo(ParcelState.CANCELLED);
            assertThat(finalComp.getStatus()).isEqualTo(CompartmentStatus.FREE);
        }
    }

    private void cleanDatabase() {
        parcelRepository.deleteAll();
        compartmentRepository.deleteAll();
        stationRepository.deleteAll();
    }

    private record RegistrationResult(ParcelResponseDto response, Throwable failure) {
        boolean succeeded() {
            return failure == null;
        }
    }

    private record PickupResult(Parcel parcel, Throwable failure) {
        boolean succeeded() {
            return failure == null;
        }
    }

    private record OperationResult(String type, Parcel parcel, Throwable failure) {
        boolean succeeded() {
            return failure == null;
        }
    }
}
