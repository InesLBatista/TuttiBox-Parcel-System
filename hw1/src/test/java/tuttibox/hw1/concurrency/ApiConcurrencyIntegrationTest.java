package tuttibox.hw1.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;
import tuttibox.hw1.TestcontainersConfiguration;
import tuttibox.hw1.dto.ParcelRequestDto;
import tuttibox.hw1.dto.PickUpRequestDto;
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
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApiConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private CompartmentRepository compartmentRepository;

    @Autowired
    private ParcelRepository parcelRepository;

    @Autowired
    private ParcelService parcelService;

    @Autowired
    private InMemoryHardwareFake hardwareFake;

    private Station station;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        station = stationRepository.save(new Station("ST-API-CONC", "Estação API Concorrência", 40.64, -8.64));
        hardwareFake.setInjectFailure(false);
        hardwareFake.setInjectCloseFailure(false);
        hardwareFake.setInjectDoorNotClosed(false);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("API REST Concorrência: 6 pedidos simultâneos a POST /api/parcels para 2 compartimentos resultam em 2 HTTP 201 e 4 HTTP 400")
    void testConcurrentHttpParcelRegistrations() throws Exception {
        compartmentRepository.save(new Compartment("C-A1", station, CompartmentSize.S));
        compartmentRepository.save(new Compartment("C-A2", station, CompartmentSize.M));

        int threadCount = 6;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final String partnerId = "HTTP_PARTNER_" + i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);

                ParcelRequestDto dto = new ParcelRequestDto();
                dto.setStationId(station.getCode());
                dto.setCompartmentSize("S");
                dto.setRecipientContact(partnerId.toLowerCase() + "@example.com");
                dto.setPartnerId(partnerId);

                MvcResult result = mockMvc.perform(post("/api/parcels")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andReturn();

                return result.getResponse().getStatus();
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get(15, TimeUnit.SECONDS));
        }
        executor.shutdownNow();

        long createdCount = statuses.stream().filter(s -> s == 201).count();
        long conflictCount = statuses.stream().filter(s -> s == 409).count();

        assertThat(createdCount).isEqualTo(2);
        assertThat(conflictCount).isEqualTo(4);
        assertThat(parcelRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("API REST Concorrência: 2 chamadas simultâneas a POST /api/parcels/pickup com o mesmo código têm exatamente 1 HTTP 200 e 1 HTTP 400")
    void testConcurrentHttpPickupRequests() throws Exception {
        compartmentRepository.save(new Compartment("C-HTTP-PICK", station, CompartmentSize.L));

        ParcelRequestDto request = new ParcelRequestDto();
        request.setStationId(station.getCode());
        request.setCompartmentSize("L");
        request.setRecipientContact("recip@example.com");
        request.setPartnerId("LOJA_C");
        var resp = parcelService.registerParcel(request);

        parcelService.processCourierDropOff(resp.trackingCode(), station.getCode(), "C-HTTP-PICK", "COURIER_1");
        parcelService.confirmCourierDropOff(resp.trackingCode(), station.getCode(), "C-HTTP-PICK", "COURIER_1");

        String rawCode = PickupCode.generate();
        Parcel parcel = parcelRepository.findByTrackingCode(resp.trackingCode()).orElseThrow();
        parcel.setPickupCodeHash(PickupCode.hash(rawCode));
        parcelRepository.save(parcel);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);

                PickUpRequestDto dto = new PickUpRequestDto();
                dto.setPickupCode(rawCode);

                MvcResult result = mockMvc.perform(post("/api/parcels/pickup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andReturn();

                return result.getResponse().getStatus();
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        int status1 = futures.get(0).get(15, TimeUnit.SECONDS);
        int status2 = futures.get(1).get(15, TimeUnit.SECONDS);
        executor.shutdownNow();

        List<Integer> list = List.of(status1, status2);
        assertThat(list).containsExactlyInAnyOrder(200, 400);

        Parcel finalParcel = parcelRepository.findByTrackingCode(resp.trackingCode()).orElseThrow();
        assertThat(finalParcel.getCurrentState()).isEqualTo(ParcelState.PICKED_UP);
        assertThat(finalParcel.getPickupCodeHash()).isNull();

        Compartment finalComp = compartmentRepository.findByStationCodeAndCode(station.getCode(), "C-HTTP-PICK").orElseThrow();
        assertThat(finalComp.getStatus()).isEqualTo(CompartmentStatus.FREE);
    }

    private void cleanDatabase() {
        parcelRepository.deleteAll();
        compartmentRepository.deleteAll();
        stationRepository.deleteAll();
    }
}
