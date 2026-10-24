package tuttibox.hw1.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import tuttibox.hw1.TestcontainersConfiguration;
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
import tuttibox.hw1.service.PickupCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest 
@AutoConfigureMockMvc 
@Transactional 
@Import(TestcontainersConfiguration.class)
public class RecipientPickUpIntegrationTest {
    private static final String STATION_CODE = "STATION_PICKUP";
    private static final String COMPARTMENT_CODE = "COMP_PICKUP";
    private static final String PICKUP_CODE = "ABCD2345";

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
    private InMemoryHardwareFake hardwareFake;

    @BeforeEach 
    void setUp() {
        parcelRepository.deleteAll();
        hardwareFake.setInjectFailure(false);
        hardwareFake.setInjectCloseFailure(false);
        hardwareFake.setInjectDoorNotClosed(false);
        hardwareFake.confirmDoorClosed(STATION_CODE, COMPARTMENT_CODE);
    }

    @Test 
    @DisplayName("destinatário recolhe por código, compartimento abre e fica livre")
    void testRecipientPickUpByCode() throws Exception {
        Parcel parcel = saveReadyParcel("TRK-PICKUP-123", PICKUP_CODE);

        mockMvc.perform(post("/api/parcels/pickup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pickupRequest("abcd2345"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingCode").value("TRK-PICKUP-123"))
                .andExpect(jsonPath("$.currentState").value("PICKED_UP"))
                .andExpect(jsonPath("$.pickupCodeHash").doesNotExist());

        Parcel updated = parcelRepository.findById(parcel.getId()).orElseThrow();
        assertThat(updated.getCurrentState()).isEqualTo(ParcelState.PICKED_UP);
        assertThat(updated.getPickupCodeHash()).isNull();
        assertThat(compartmentRepository.findById(parcel.getAssignedCompartment().getId())
                .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.FREE);
        assertThat(hardwareFake.isDoorClosed(STATION_CODE, COMPARTMENT_CODE)).isFalse();
    }

    @Test
    @DisplayName("código de recolha só pode ser usado uma vez")
    void testPickupCodeIsSingleUse() throws Exception {
        saveReadyParcel("TRK-PICKUP-456", PICKUP_CODE);
        String body = objectMapper.writeValueAsString(pickupRequest(PICKUP_CODE));

        mockMvc.perform(post("/api/parcels/pickup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/parcels/pickup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("código de recolha inválido"));

        Parcel unchanged = parcelRepository.findByTrackingCode("TRK-PICKUP-456").orElseThrow();
        assertThat(unchanged.getCurrentState()).isEqualTo(ParcelState.PICKED_UP);
        assertThat(unchanged.getPickupCodeHash()).isNull();
    }

    @Test
    @DisplayName("código errado não abre o compartimento nem altera o estado")
    void testWrongPickupCode() throws Exception {
        Parcel parcel = saveReadyParcel("TRK-PICKUP-789", PICKUP_CODE);

        mockMvc.perform(post("/api/parcels/pickup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pickupRequest("ZZZZ9999"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("código de recolha inválido"));

        Parcel unchanged = parcelRepository.findById(parcel.getId()).orElseThrow();
        assertThat(unchanged.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        assertThat(unchanged.getPickupCodeHash()).isNotBlank();
        assertThat(unchanged.getAssignedCompartment().getStatus()).isEqualTo(CompartmentStatus.OCCUPIED);
        assertThat(hardwareFake.isDoorClosed(STATION_CODE, COMPARTMENT_CODE)).isTrue();
    }

    @Test
    @DisplayName("código de encomenda expirada é rejeitado sem abrir o compartimento")
    void testExpiredParcelPickupRejected() throws Exception {
        Parcel parcel = saveReadyParcel("TRK-PICKUP-EXP", PICKUP_CODE);
        parcel.expire("SYSTEM");
        parcelRepository.save(parcel);

        mockMvc.perform(post("/api/parcels/pickup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pickupRequest(PICKUP_CODE))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        Parcel unchanged = parcelRepository.findById(parcel.getId()).orElseThrow();
        assertThat(unchanged.getCurrentState()).isEqualTo(ParcelState.EXPIRED);
        assertThat(hardwareFake.isDoorClosed(STATION_CODE, COMPARTMENT_CODE)).isTrue();
    }

    @Test
    @DisplayName("falha de hardware não consome o código e permite nova tentativa")
    void testHardwareFailureKeepsCodeValid() throws Exception {
        saveReadyParcel("TRK-PICKUP-HW", PICKUP_CODE);
        hardwareFake.setInjectFailure(true);

        mockMvc.perform(post("/api/parcels/pickup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pickupRequest(PICKUP_CODE))))
                .andExpect(status().isServiceUnavailable());

        Parcel unchanged = parcelRepository.findByTrackingCode("TRK-PICKUP-HW").orElseThrow();
        assertThat(unchanged.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        assertThat(unchanged.getPickupCodeHash()).isNotBlank();
        assertThat(unchanged.getAssignedCompartment().getStatus()).isEqualTo(CompartmentStatus.OCCUPIED);

        hardwareFake.setInjectFailure(false);

        mockMvc.perform(post("/api/parcels/pickup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pickupRequest(PICKUP_CODE))))
                .andExpect(status().isOk());

        Parcel updated = parcelRepository.findByTrackingCode("TRK-PICKUP-HW").orElseThrow();
        assertThat(updated.getCurrentState()).isEqualTo(ParcelState.PICKED_UP);
        assertThat(updated.getAssignedCompartment().getStatus()).isEqualTo(CompartmentStatus.FREE);
    }

    private PickUpRequestDto pickupRequest(String pickupCode) {
        PickUpRequestDto request = new PickUpRequestDto();
        request.setPickupCode(pickupCode);
        return request;
    }

    private Parcel saveReadyParcel(String trackingCode, String pickupCode) {
        Station station = stationRepository.save(
            new Station(STATION_CODE, "Estação Pickup", 40.63, -8.65));
        Compartment compartment = new Compartment(
            COMPARTMENT_CODE, station, CompartmentSize.M);
        compartment.reserve();
        compartment.occupy();
        compartmentRepository.save(compartment);

        Parcel parcel = new Parcel(trackingCode, "client@example.com", "M");
        parcel.setStation(station);
        parcel.setAssignedCompartment(compartment);
        parcel.markAsReadyForPickup("COURIER_01");
        parcel.issuePickupCode(PickupCode.hash(pickupCode));
        return parcelRepository.save(parcel);
    }
}
