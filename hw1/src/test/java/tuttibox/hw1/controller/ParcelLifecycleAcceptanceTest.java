package tuttibox.hw1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tuttibox.hw1.TestcontainersConfiguration;
import tuttibox.hw1.dto.DropOffRequestDto;
import tuttibox.hw1.dto.ParcelRequestDto;
import tuttibox.hw1.dto.PickUpRequestDto;
import tuttibox.hw1.fake.InMemoryHardwareFake;
import tuttibox.hw1.fake.InMemoryNotificationFake;
import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.CompartmentStatus;
import tuttibox.hw1.model.Station;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.ParcelRepository;
import tuttibox.hw1.repository.StationRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class ParcelLifecycleAcceptanceTest {
    private static final String STATION_CODE = "ST-ACCEPTANCE";
    private static final String COURIER_ID = "COURIER_ACCEPTANCE";
    private static final String OPERATOR_ID = "OPERATOR_ACCEPTANCE";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ParcelRepository parcelRepository;
    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private CompartmentRepository compartmentRepository;
    @Autowired
    private InMemoryNotificationFake notificationFake;
    @Autowired
    private InMemoryHardwareFake hardwareFake;

    @BeforeEach
    void setUp() {
        parcelRepository.deleteAll();
        compartmentRepository.deleteAll();
        stationRepository.deleteAll();
        notificationFake.clear();
        hardwareFake.setInjectFailure(false);
        hardwareFake.setInjectCloseFailure(false);
        hardwareFake.setInjectDoorNotClosed(false);

        Station station = stationRepository.save(
            new Station(STATION_CODE, "Estação de aceitação", 40.6405, -8.6538));
        compartmentRepository.save(new Compartment("C-S1", station, CompartmentSize.S));
        compartmentRepository.save(new Compartment("C-M1", station, CompartmentSize.M));
        compartmentRepository.save(new Compartment("C-L1", station, CompartmentSize.L));
    }

    @Test
    @DisplayName("aceitação: registo, drop-off, confirmação, pickup e tracking")
    void testCompleteLifecycleUntilPickup() throws Exception {
        Registration registration = registerParcel("M");
        dropOffAndConfirm(registration);

        InMemoryNotificationFake.Notification notification = notificationFake
            .findLastByTrackingCode(registration.trackingCode())
            .orElseThrow();

        mockMvc.perform(get("/api/parcels/{trackingCode}/status", registration.trackingCode()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value("READY_FOR_PICKUP"));

        mockMvc.perform(post("/api/parcels/pickup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pickupRequest(notification.pickupCode()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trackingCode").value(registration.trackingCode()))
            .andExpect(jsonPath("$.currentState").value("PICKED_UP"))
            .andExpect(jsonPath("$.pickupCodeHash").doesNotExist());

        mockMvc.perform(get("/api/parcels/{trackingCode}/status", registration.trackingCode()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value("PICKED_UP"));

        mockMvc.perform(get("/api/parcels/{trackingCode}/history", registration.trackingCode()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].toState").value("CREATED"))
            .andExpect(jsonPath("$[0].actor").value("PARTNER"))
            .andExpect(jsonPath("$[1].toState").value("READY_FOR_PICKUP"))
            .andExpect(jsonPath("$[1].actor").value(COURIER_ID))
            .andExpect(jsonPath("$[2].toState").value("PICKED_UP"))
            .andExpect(jsonPath("$[2].actor").value("RECIPIENT"));

        assertThat(compartmentStatus(registration.compartmentCode()))
            .isEqualTo(CompartmentStatus.FREE);
    }

    @Test
    @DisplayName("aceitação: expiração e remoção pelo operador libertam o compartimento")
    void testCompleteLifecycleWithExpirationAndOperatorRemoval() throws Exception {
        Registration registration = registerParcel("M");
        dropOffAndConfirm(registration);

        mockMvc.perform(post("/api/parcels/{trackingCode}/expire", registration.trackingCode())
                .param("actor", "SYSTEM"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentState").value("EXPIRED"));

        mockMvc.perform(get("/api/operator/parcels/expired").param("stationId", STATION_CODE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].trackingCode").value(registration.trackingCode()));

        assertThat(compartmentStatus(registration.compartmentCode()))
            .isEqualTo(CompartmentStatus.OCCUPIED);

        mockMvc.perform(post("/api/operator/parcels/{trackingCode}/remove", registration.trackingCode())
                .param("actor", OPERATOR_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentState").value("RETURNED"))
            .andExpect(jsonPath("$.assignedCompartmentCode").value(registration.compartmentCode()));

        mockMvc.perform(get("/api/parcels/{trackingCode}/history", registration.trackingCode()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(4))
            .andExpect(jsonPath("$[2].toState").value("EXPIRED"))
            .andExpect(jsonPath("$[2].actor").value("SYSTEM"))
            .andExpect(jsonPath("$[3].toState").value("RETURNED"))
            .andExpect(jsonPath("$[3].actor").value(OPERATOR_ID));

        assertThat(compartmentStatus(registration.compartmentCode()))
            .isEqualTo(CompartmentStatus.FREE);
    }

    private Registration registerParcel(String size) throws Exception {
        ParcelRequestDto request = new ParcelRequestDto();
        request.setStationId(STATION_CODE);
        request.setCompartmentSize(size);
        request.setRecipientContact("destinatario@example.com");
        request.setPartnerId("PARTNER_ACCEPTANCE");

        MvcResult result = mockMvc.perform(post("/api/parcels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.currentState").value("CREATED"))
            .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Registration(
            body.get("trackingCode").asString(),
            body.get("assignedCompartmentCode").asString());
    }

    private void dropOffAndConfirm(Registration registration) throws Exception {
        DropOffRequestDto request = new DropOffRequestDto();
        request.setStationId(STATION_CODE);
        request.setCompartmentId(registration.compartmentCode());
        request.setCourierId(COURIER_ID);

        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/parcels/{trackingCode}/dropoff", registration.trackingCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentState").value("CREATED"));

        assertThat(compartmentStatus(registration.compartmentCode()))
            .isEqualTo(CompartmentStatus.RESERVED);

        mockMvc.perform(post("/api/parcels/{trackingCode}/dropoff/confirm", registration.trackingCode())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentState").value("READY_FOR_PICKUP"));

        assertThat(compartmentStatus(registration.compartmentCode()))
            .isEqualTo(CompartmentStatus.OCCUPIED);
    }

    private PickUpRequestDto pickupRequest(String pickupCode) {
        PickUpRequestDto request = new PickUpRequestDto();
        request.setPickupCode(pickupCode);
        return request;
    }

    private CompartmentStatus compartmentStatus(String compartmentCode) {
        return compartmentRepository.findByStationCodeAndCode(STATION_CODE, compartmentCode)
            .orElseThrow()
            .getStatus();
    }

    private record Registration(String trackingCode, String compartmentCode) {
    }
}
