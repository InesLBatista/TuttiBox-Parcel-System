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
import tuttibox.hw1.dto.DropOffRequestDto;
import tuttibox.hw1.fake.InMemoryHardwareFake;
import tuttibox.hw1.fake.InMemoryNotificationFake;
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
public class CourierDropOffIntegrationTest {
    @Autowired 
    private MockMvc mockMvc;
    @Autowired 
    private ObjectMapper objectMapper;
    @Autowired 
    private ParcelRepository parcelRepository;
    @Autowired
    private InMemoryNotificationFake notificationFake;
    @Autowired
    private InMemoryHardwareFake hardwareFake;
    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private CompartmentRepository compartmentRepository;

    @BeforeEach 
    void setUp() {
        parcelRepository.deleteAll();
        notificationFake.clear();
        hardwareFake.setInjectFailure(false);
        hardwareFake.setInjectCloseFailure(false);
        hardwareFake.setInjectDoorNotClosed(false);
    }

    @Test 
    @DisplayName ("dropoff abre a porta e a confirmação de fecho transita para READY_FOR_PICKUP")
    void testCourierDropOffApi() throws Exception {
        saveParcelWithCompartment("TRK-TEST123", "client@example.com", "M", "COMP_M1", "STATION_01");

        //execução do pedido post para endpoint de dropoff
        mockMvc.perform(post("/api/parcels/TRK-TEST123/dropoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dropOffDto())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingCode").value("TRK-TEST123"))
                .andExpect(jsonPath("$.currentState").value("CREATED"))
                .andExpect(jsonPath("$.pickupCodeHash").doesNotExist());

        Parcel openedParcel = parcelRepository.findByTrackingCode("TRK-TEST123").orElseThrow();
        assertThat(openedParcel.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(openedParcel.getPickupCodeHash()).isNull();
        assertThat(compartmentRepository.findByStationCodeAndCode("STATION_01", "COMP_M1")
                .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.RESERVED);
        assertThat(hardwareFake.isDoorClosed("STATION_01", "COMP_M1")).isFalse();
        assertThat(notificationFake.getNotifications()).isEmpty();

        //a confirmação de fecho só deve ser aceite depois de a porta estar fechada
        mockMvc.perform(post("/api/parcels/TRK-TEST123/dropoff/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dropOffDto())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState").value("READY_FOR_PICKUP"))
                .andExpect(jsonPath("$.pickupCodeHash").doesNotExist());

        Parcel updatedParcel = parcelRepository.findByTrackingCode("TRK-TEST123").orElseThrow();
        assertThat(updatedParcel.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        assertThat(updatedParcel.getPickupCodeHash()).isNotBlank();
        assertThat(compartmentRepository.findByStationCodeAndCode("STATION_01", "COMP_M1")
                .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.OCCUPIED);
        assertThat(hardwareFake.isDoorClosed("STATION_01", "COMP_M1")).isTrue();

        InMemoryNotificationFake.Notification notification = notificationFake
            .findLastByTrackingCode("TRK-TEST123")
            .orElseThrow();
        assertThat(notification.recipientContact()).isEqualTo("client@example.com");
        assertThat(notification.pickupCode()).matches("[A-HJ-NP-Z2-9]{8}");
        assertThat(PickupCode.matches(notification.pickupCode(), updatedParcel.getPickupCodeHash()))
            .isTrue();
    }

    @Test
    @DisplayName("dropoff com compartimento diferente do atribuído devolve 400 e mantém RESERVED")
    void testDropOffWithDifferentCompartmentReturnsBadRequest() throws Exception {
        saveParcelWithCompartment("TRK-TEST126", "client@example.com", "M", "COMP_M1", "STATION_01");
        DropOffRequestDto request = dropOffDto();
        request.setCompartmentId("COMP_M2");

        mockMvc.perform(post("/api/parcels/TRK-TEST126/dropoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                    "não corresponde ao compartimento atribuído")));

        Parcel unchanged = parcelRepository.findByTrackingCode("TRK-TEST126").orElseThrow();
        assertThat(unchanged.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(compartmentRepository.findByStationCodeAndCode("STATION_01", "COMP_M1")
                .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.RESERVED);
        assertThat(notificationFake.getNotifications()).isEmpty();
    }

    @Test
    @DisplayName("confirmação com porta ainda aberta devolve 503 e mantém CREATED")
    void testConfirmationWithOpenDoorReturnsServiceUnavailable() throws Exception {
        saveParcelWithCompartment("TRK-TEST124", "client@example.com", "M", "COMP_M1", "STATION_01");

        mockMvc.perform(post("/api/parcels/TRK-TEST124/dropoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dropOffDto())))
                .andExpect(status().isOk());

        hardwareFake.setInjectDoorNotClosed(true);

        mockMvc.perform(post("/api/parcels/TRK-TEST124/dropoff/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dropOffDto())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        Parcel unchanged = parcelRepository.findByTrackingCode("TRK-TEST124").orElseThrow();
        assertThat(unchanged.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(unchanged.getPickupCodeHash()).isNull();
        assertThat(compartmentRepository.findByStationCodeAndCode("STATION_01", "COMP_M1")
                .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.RESERVED);
        assertThat(notificationFake.getNotifications()).isEmpty();
    }

    @Test
    @DisplayName("falha de hardware na confirmação de fecho devolve 503 e mantém CREATED")
    void testConfirmationHardwareFailureReturnsServiceUnavailable() throws Exception {
        saveParcelWithCompartment("TRK-TEST125", "client@example.com", "M", "COMP_M1", "STATION_01");

        mockMvc.perform(post("/api/parcels/TRK-TEST125/dropoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dropOffDto())))
                .andExpect(status().isOk());

        hardwareFake.setInjectCloseFailure(true);

        mockMvc.perform(post("/api/parcels/TRK-TEST125/dropoff/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dropOffDto())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        Parcel unchanged = parcelRepository.findByTrackingCode("TRK-TEST125").orElseThrow();
        assertThat(unchanged.getCurrentState()).isEqualTo(ParcelState.CREATED);
        assertThat(unchanged.getPickupCodeHash()).isNull();
        assertThat(compartmentRepository.findByStationCodeAndCode("STATION_01", "COMP_M1")
                .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.RESERVED);
        assertThat(notificationFake.getNotifications()).isEmpty();
    }

    @Test
    @DisplayName("dropoff de encomenda inexistente devolve 404")
    void testDropOffUnknownParcelReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/parcels/TRK-DOES-NOT-EXIST/dropoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dropOffDto())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("confirmação de encomenda inexistente devolve 404")
    void testConfirmUnknownParcelReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/parcels/TRK-DOES-NOT-EXIST/dropoff/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dropOffDto())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
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

    private DropOffRequestDto dropOffDto() {
        DropOffRequestDto dropOffDto = new DropOffRequestDto();
        dropOffDto.setStationId("STATION_01");
        dropOffDto.setCompartmentId("COMP_M1");
        dropOffDto.setCourierId("COURIER_123");
        return dropOffDto;
    }
}
