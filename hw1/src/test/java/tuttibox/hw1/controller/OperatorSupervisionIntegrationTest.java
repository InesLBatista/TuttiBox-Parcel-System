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
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import tuttibox.hw1.TestcontainersConfiguration;
import tuttibox.hw1.dto.ParcelRequestDto;
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
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class OperatorSupervisionIntegrationTest {
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

    @BeforeEach
    void setUp() {
        parcelRepository.deleteAll();
        compartmentRepository.deleteAll();
        stationRepository.deleteAll();
    }

    @Test
    @DisplayName("lista encomendas por estação e por estado")
    void testListParcelsByStationAndState() throws Exception {
        Station stationA = station("ST-A");
        Station stationB = station("ST-B");
        parcelRepository.save(parcel("TRK-A-CREATED", stationA, null, ParcelState.CREATED));
        parcelRepository.save(parcel("TRK-A-READY", stationA, null, ParcelState.READY_FOR_PICKUP));
        parcelRepository.save(parcel("TRK-B-READY", stationB, null, ParcelState.READY_FOR_PICKUP));

        mockMvc.perform(get("/api/operator/parcels")
                .param("stationId", "ST-A")
                .param("state", "READY_FOR_PICKUP"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].trackingCode").value("TRK-A-READY"))
            .andExpect(jsonPath("$[0].currentState").value("READY_FOR_PICKUP"));

        mockMvc.perform(get("/api/operator/parcels").param("stationId", "ST-A"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/operator/parcels").param("state", "READY_FOR_PICKUP"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/operator/parcels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("lista de encomendas de uma estação inexistente devolve 404")
    void testListParcelsUnknownStation() throws Exception {
        mockMvc.perform(get("/api/operator/parcels").param("stationId", "UNKNOWN"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("estação não encontrada: UNKNOWN"));
    }

    @Test
    @DisplayName("lista encomendas expiradas, opcionalmente filtradas por estação")
    void testListExpiredParcels() throws Exception {
        Station stationA = station("ST-A");
        Station stationB = station("ST-B");
        parcelRepository.save(parcel("TRK-A-EXPIRED", stationA, null, ParcelState.EXPIRED));
        parcelRepository.save(parcel("TRK-B-EXPIRED", stationB, null, ParcelState.EXPIRED));
        parcelRepository.save(parcel("TRK-A-READY", stationA, null, ParcelState.READY_FOR_PICKUP));

        mockMvc.perform(get("/api/operator/parcels/expired"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/operator/parcels/expired").param("stationId", "ST-A"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].trackingCode").value("TRK-A-EXPIRED"))
            .andExpect(jsonPath("$[0].currentState").value("EXPIRED"));
    }

    @Test
    @DisplayName("remoção de encomenda expirada liberta o compartimento")
    void testRemoveExpiredParcelReleasesCompartment() throws Exception {
        Station station = station("ST-REMOVE");
        Compartment compartment = compartment(station, "C-1", CompartmentSize.M, CompartmentStatus.OCCUPIED);
        Parcel parcel = parcel("TRK-REMOVE", station, compartment, ParcelState.EXPIRED);
        parcelRepository.save(parcel);

        mockMvc.perform(post("/api/operator/parcels/TRK-REMOVE/remove")
                .param("actor", "OPERATOR_01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trackingCode").value("TRK-REMOVE"))
            .andExpect(jsonPath("$.currentState").value("RETURNED"))
            .andExpect(jsonPath("$.assignedCompartmentCode").value("C-1"));

        Parcel updated = parcelRepository.findByTrackingCode("TRK-REMOVE").orElseThrow();
        assertThat(updated.getCurrentState()).isEqualTo(ParcelState.RETURNED);
        assertThat(compartmentRepository.findByStationCodeAndCode("ST-REMOVE", "C-1")
                .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.FREE);
    }

    @Test
    @DisplayName("não é possível remover uma encomenda antes de expirar")
    void testRemoveNonExpiredParcelIsRejected() throws Exception {
        Station station = station("ST-NOT-EXPIRED");
        Compartment compartment = compartment(station, "C-1", CompartmentSize.S, CompartmentStatus.OCCUPIED);
        parcelRepository.save(parcel("TRK-READY", station, compartment, ParcelState.READY_FOR_PICKUP));

        mockMvc.perform(post("/api/operator/parcels/TRK-READY/remove")
                .param("actor", "OPERATOR_01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("expiraram")));

        Parcel unchanged = parcelRepository.findByTrackingCode("TRK-READY").orElseThrow();
        assertThat(unchanged.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
        assertThat(compartmentRepository.findByStationCodeAndCode("ST-NOT-EXPIRED", "C-1")
                .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.OCCUPIED);
    }

    @Test
    @DisplayName("operação de supervisão exige ator")
    void testSupervisionOperationRequiresActor() throws Exception {
        Station station = station("ST-ACTOR");
        parcelRepository.save(parcel("TRK-ACTOR", station, null, ParcelState.EXPIRED));

        mockMvc.perform(post("/api/operator/parcels/TRK-ACTOR/remove").param("actor", " "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("o ator é obrigatório"));
    }

    @Test
    @DisplayName("compartimento pode ficar fora de serviço e voltar ao serviço")
    void testCompartmentOutOfServiceAndBackInService() throws Exception {
        Station station = station("ST-SERVICE");
        compartment(station, "C-1", CompartmentSize.S, CompartmentStatus.FREE);

        mockMvc.perform(post("/api/operator/stations/ST-SERVICE/compartments/C-1/out-of-service")
                .param("actor", "OPERATOR_01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stationCode").value("ST-SERVICE"))
            .andExpect(jsonPath("$.code").value("C-1"))
            .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));

        mockMvc.perform(get("/api/operator/stations/ST-SERVICE/compartments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("OUT_OF_SERVICE"));

        mockMvc.perform(post("/api/operator/stations/ST-SERVICE/compartments/C-1/in-service")
                .param("actor", "OPERATOR_01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FREE"));

        assertThat(compartmentRepository.findByStationCodeAndCode("ST-SERVICE", "C-1")
                .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.FREE);
    }

    @Test
    @DisplayName("compartimento fora de serviço nunca é alocado")
    void testOutOfServiceCompartmentIsNotAllocated() throws Exception {
        Station station = station("ST-ALLOC");
        compartment(station, "C-S1", CompartmentSize.S, CompartmentStatus.OUT_OF_SERVICE);
        compartment(station, "C-M1", CompartmentSize.M, CompartmentStatus.FREE);

        mockMvc.perform(post("/api/parcels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest("ST-ALLOC", "S"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.assignedCompartmentCode").value("C-M1"));

        assertThat(compartmentRepository.findByStationCodeAndCode("ST-ALLOC", "C-S1")
                .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.OUT_OF_SERVICE);
        assertThat(compartmentRepository.findByStationCodeAndCode("ST-ALLOC", "C-M1")
                .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.RESERVED);
    }

    @Test
    @DisplayName("estação sem compartimentos livres devolve conflito mesmo com compartimento fora de serviço")
    void testOnlyOutOfServiceCompartmentReturnsConflict() throws Exception {
        Station station = station("ST-FULL");
        compartment(station, "C-S1", CompartmentSize.S, CompartmentStatus.OUT_OF_SERVICE);

        mockMvc.perform(post("/api/parcels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest("ST-FULL", "S"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("não é possível pôr fora de serviço um compartimento ocupado")
    void testOutOfServiceOccupiedCompartmentIsRejected() throws Exception {
        Station station = station("ST-OCCUPIED");
        compartment(station, "C-1", CompartmentSize.M, CompartmentStatus.OCCUPIED);

        mockMvc.perform(post("/api/operator/stations/ST-OCCUPIED/compartments/C-1/out-of-service")
                .param("actor", "OPERATOR_01"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("OCCUPIED")));

        assertThat(compartmentRepository.findByStationCodeAndCode("ST-OCCUPIED", "C-1")
                .orElseThrow().getStatus()).isEqualTo(CompartmentStatus.OCCUPIED);
    }

    @Test
    @DisplayName("não é possível voltar ao serviço um compartimento que não está fora de serviço")
    void testInServiceFreeCompartmentIsRejected() throws Exception {
        Station station = station("ST-FREE");
        compartment(station, "C-1", CompartmentSize.S, CompartmentStatus.FREE);

        mockMvc.perform(post("/api/operator/stations/ST-FREE/compartments/C-1/in-service")
                .param("actor", "OPERATOR_01"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("não está fora de serviço")));
    }

    @Test
    @DisplayName("compartimento ou estação inexistente devolve 404")
    void testUnknownCompartmentOrStation() throws Exception {
        Station station = station("ST-404");

        mockMvc.perform(post("/api/operator/stations/ST-404/compartments/MISSING/out-of-service")
                .param("actor", "OPERATOR_01"))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/operator/stations/MISSING/compartments"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("estação não encontrada: MISSING"));

        assertThat(stationRepository.findByCode(station.getCode())).isPresent();
    }

    private Station station(String code) {
        return stationRepository.save(new Station(code, "Estação " + code, 40.63, -8.65));
    }

    private Compartment compartment(
            Station station, String code, CompartmentSize size, CompartmentStatus status) {
        Compartment compartment = new Compartment(code, station, size);
        compartment.setStatus(status);
        return compartmentRepository.save(compartment);
    }

    private Parcel parcel(
            String trackingCode, Station station, Compartment compartment, ParcelState state) {
        Parcel parcel = new Parcel(trackingCode, "client@example.com", "M");
        parcel.setStation(station);
        parcel.setAssignedCompartment(compartment);
        if (state == ParcelState.READY_FOR_PICKUP || state == ParcelState.EXPIRED) {
            parcel.markAsReadyForPickup("COURIER_01");
        }
        if (state == ParcelState.EXPIRED) {
            parcel.expire("SYSTEM");
        }
        return parcel;
    }

    private ParcelRequestDto registerRequest(String stationId, String size) {
        ParcelRequestDto request = new ParcelRequestDto();
        request.setStationId(stationId);
        request.setCompartmentSize(size);
        request.setRecipientContact("client@example.com");
        request.setPartnerId("PARTNER_01");
        return request;
    }
}
