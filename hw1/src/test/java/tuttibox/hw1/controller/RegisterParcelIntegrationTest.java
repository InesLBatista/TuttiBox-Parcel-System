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
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import tuttibox.hw1.TestcontainersConfiguration;
import tuttibox.hw1.dto.ParcelRequestDto;
import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.CompartmentStatus;
import tuttibox.hw1.model.Station;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.StationRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class RegisterParcelIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired 
    private ObjectMapper objectMapper;
    @Autowired
    private StationRepository stationRepository;
    @Autowired 
    private CompartmentRepository compartmentRepository;

    @BeforeEach
    void setUp() {
        compartmentRepository.deleteAll();
        stationRepository.deleteAll();

        Station st = stationRepository.save(new Station("ST-01", "Estação Centro", 40.63, -8.65));
        compartmentRepository.save(new Compartment("C-S1", st, CompartmentSize.S));
        compartmentRepository.save(new Compartment("C-M1", st, CompartmentSize.M));
        compartmentRepository.save(new Compartment("C-L1", st, CompartmentSize.L));
    }

    @Test
    @DisplayName("registo com sucesso, reserva o menor compartimento compatível")
    void testRegisterParcelReservesCompartment() throws Exception {
        ParcelRequestDto dto = buildDto("ST-01", "M", "client@example.com", "PARTNER_01");

        mockMvc.perform(post("/api/parcels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingCode").exists())
                .andExpect(jsonPath("$.currentState").value("CREATED"))
                .andExpect(jsonPath("$.compartmentSize").value("M"))
                .andExpect(jsonPath("$.assignedCompartmentCode").value("C-M1"))
                .andExpect(jsonPath("$.assignedStationCode").value("ST-01"));

        //o compartimento M deve ter ficado RESERVED
        Compartment m = compartmentRepository.findByStationCodeAndCode("ST-01", "C-M1").orElseThrow();
        assertThat(m.getStatus()).isEqualTo(CompartmentStatus.RESERVED);

        //os outros ficam completamente disponíveis para serem usados em outras encomendas
        Compartment s = compartmentRepository.findByStationCodeAndCode("ST-01", "C-S1").orElseThrow();
        assertThat(s.getStatus()).isEqualTo(CompartmentStatus.FREE);
    }

    @Test
    @DisplayName("pedido de tamanho S usa o compartimento S (menor compatível)")
    void testRegisterParcelPicksSmallestFit() throws Exception {
        ParcelRequestDto dto = buildDto("ST-01", "S", "a@b.com", "P1");

        mockMvc.perform(post("/api/parcels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignedCompartmentCode").value("C-S1"));
    }

    @Test
    @DisplayName("sem compartimento disponível devolve 409 Conflict")
    void testRegisterParcelNoCompartmentAvailable() throws Exception {
        //reservar todos os M e L e tentar colocar na station uma encomenda M
        Compartment m = compartmentRepository.findByStationCodeAndCode("ST-01", "C-M1").orElseThrow();
        Compartment l = compartmentRepository.findByStationCodeAndCode("ST-01", "C-L1").orElseThrow();
        m.reserve(); compartmentRepository.save(m);
        l.reserve(); compartmentRepository.save(l);

        ParcelRequestDto dto = buildDto("ST-01", "M", "client@example.com", "PARTNER_01");

        mockMvc.perform(post("/api/parcels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("estação inexistente devolve 404")
    void testRegisterParcelUnknownStation() throws Exception {
        ParcelRequestDto dto = buildDto("ST-INEXISTENTE", "S", "client@example.com", "PARTNER_01");

        mockMvc.perform(post("/api/parcels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("tamanho inválido devolve 400")
    void testRegisterParcelInvalidSize() throws Exception {
        ParcelRequestDto dto = buildDto("ST-01", "XL", "client@example.com", "PARTNER_01");

        mockMvc.perform(post("/api/parcels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("stationId vazio devolve 400")
    void testRegisterParcelMissingStation() throws Exception {
        ParcelRequestDto dto = buildDto("", "M", "client@example.com", "PARTNER_01");

        mockMvc.perform(post("/api/parcels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
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
