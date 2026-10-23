package tuttibox.hw1.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
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
class DuplicateParcelRegistrationIntegrationTest {
    //especificação do comportamento de POST /api/parcels quando há pedidos duplicados
    //como defini pedido como não tendo parcel's identification e o tracking code é gerado pelo servidor, então 2 pedidos iguais vão ser 
    //tratados como 2 registos independentes, cada 1 reserva 1 compartimento e recebe tracking code próprio.

    //quando não há compartimento compatível, pedido deve ser rejeitado, não criando a encomenda

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

    private Station station;

    @BeforeEach
    void setUp() {
        parcelRepository.deleteAll();
        compartmentRepository.deleteAll();
        stationRepository.deleteAll();
        station = stationRepository.save(new Station("ST-DUP", "Estação Duplicados", 40.63, -8.65));
    }

    @Test
    @DisplayName("pedido repetido é tratado como novo registo e reserva outro compartimento")
    void testRepeatedRequestCreatesIndependentParcels() throws Exception {
        compartmentRepository.save(new Compartment("C-S1", station, CompartmentSize.S));
        compartmentRepository.save(new Compartment("C-S2", station, CompartmentSize.S));

        ParcelRequestDto request = buildDto("ST-DUP", "S", "cliente@example.com", "PARTNER_01");

        MvcResult first = postParcel(request)
            .andExpect(status().isCreated())
            .andReturn();
        MvcResult second = postParcel(request)
            .andExpect(status().isCreated())
            .andReturn();

        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode secondBody = objectMapper.readTree(second.getResponse().getContentAsString());

        assertThat(firstBody.get("trackingCode").asString())
            .isNotEqualTo(secondBody.get("trackingCode").asString());
        assertThat(firstBody.get("assignedCompartmentCode").asString())
            .isNotEqualTo(secondBody.get("assignedCompartmentCode").asString());

        assertThat(parcelRepository.count()).isEqualTo(2);
        assertThat(compartmentRepository.findByStationCodeAndCode("ST-DUP", "C-S1").orElseThrow().getStatus())
            .isEqualTo(CompartmentStatus.RESERVED);
        assertThat(compartmentRepository.findByStationCodeAndCode("ST-DUP", "C-S2").orElseThrow().getStatus())
            .isEqualTo(CompartmentStatus.RESERVED);
    }

    @Test
    @DisplayName("pedido repetido sem compartimento compatível devolve 409 e não cria encomenda")
    void testRepeatedRequestWhenStationIsFullReturnsConflict() throws Exception {
        compartmentRepository.save(new Compartment("C-S1", station, CompartmentSize.S));

        ParcelRequestDto request = buildDto("ST-DUP", "S", "cliente@example.com", "PARTNER_01");

        postParcel(request)
            .andExpect(status().isCreated());

        postParcel(request)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409));

        assertThat(parcelRepository.count()).isEqualTo(1);
        assertThat(compartmentRepository.findByStationCodeAndCode("ST-DUP", "C-S1").orElseThrow().getStatus())
            .isEqualTo(CompartmentStatus.RESERVED);
    }

    @Test
    @DisplayName("repetir depois de cancelar cria encomenda independente da cancelada")
    void testRepeatedRequestAfterCancellationCreatesNewParcel() throws Exception {
        compartmentRepository.save(new Compartment("C-S1", station, CompartmentSize.S));
        compartmentRepository.save(new Compartment("C-S2", station, CompartmentSize.S));

        ParcelRequestDto request = buildDto("ST-DUP", "S", "cliente@example.com", "PARTNER_01");

        MvcResult first = postParcel(request)
            .andExpect(status().isCreated())
            .andReturn();
        String firstTrackingCode = objectMapper.readTree(first.getResponse().getContentAsString())
            .get("trackingCode").asString();

        mockMvc.perform(post("/api/parcels/{trackingCode}/cancel", firstTrackingCode)
                .param("actor", "PARTNER_01"))
            .andExpect(status().isOk());

        MvcResult second = postParcel(request)
            .andExpect(status().isCreated())
            .andReturn();
        String secondTrackingCode = objectMapper.readTree(second.getResponse().getContentAsString())
            .get("trackingCode").asString();

        assertThat(secondTrackingCode).isNotEqualTo(firstTrackingCode);
        assertThat(parcelRepository.count()).isEqualTo(2);

        Parcel original = parcelRepository.findByTrackingCode(firstTrackingCode).orElseThrow();
        Parcel repeated = parcelRepository.findByTrackingCode(secondTrackingCode).orElseThrow();
        assertThat(original.getCurrentState()).isEqualTo(ParcelState.CANCELLED);
        assertThat(repeated.getCurrentState()).isEqualTo(ParcelState.CREATED);
    }

    private ResultActions postParcel(ParcelRequestDto request) throws Exception {
        return mockMvc.perform(post("/api/parcels")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));
    }

    private ParcelRequestDto buildDto(String stationId, String size, String contact, String partnerId) {
        ParcelRequestDto dto = new ParcelRequestDto();
        dto.setStationId(stationId);
        dto.setCompartmentSize(size);
        dto.setRecipientContact(contact);
        dto.setPartnerId(partnerId);
        return dto;
    }
}
