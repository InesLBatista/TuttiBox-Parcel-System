package tuttibox.hw1.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tuttibox.hw1.TestcontainersConfiguration;
import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.Station;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.StationRepository;

@SpringBootTest 
@AutoConfigureMockMvc 
@Transactional
@Import(TestcontainersConfiguration.class)
public class StationSearchControllerIntegrationTest {
    @Autowired 
    private MockMvc mockMvc;

    @Autowired 
    private StationRepository stationRepository;
    @Autowired private CompartmentRepository compartmentRepository;

    @BeforeEach
    void setUp() {
        compartmentRepository.deleteAll();
        stationRepository.deleteAll();

        Station near = stationRepository.save(new Station("ST-NEAR", "Estação Perto", 40.6305, -8.6571));
        Station far = stationRepository.save(new Station("ST-FAR", "Estação Longe", 38.7223, -9.1393));

        compartmentRepository.save(new Compartment("N-S1", near, CompartmentSize.S));
        compartmentRepository.save(new Compartment("N-S2", near, CompartmentSize.S));
        compartmentRepository.save(new Compartment("N-M1", near, CompartmentSize.M));
        compartmentRepository.save(new Compartment("F-L1", far, CompartmentSize.L));
    }

    @Test
    @DisplayName("deve devolver as estações mais próximas com disponibilidade por tamanho")
    void testSearchByAddress() throws Exception {
        mockMvc.perform(get("/api/stations/search").param("address", "Anadia"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].code").value("ST-NEAR"))
            .andExpect(jsonPath("$[0].name").value("Estação Perto"))
            .andExpect(jsonPath("$[0].distanceKm").value(0.0))
            .andExpect(jsonPath("$[0].availableSmall").value(2))
            .andExpect(jsonPath("$[0].availableMedium").value(1))
            .andExpect(jsonPath("$[0].availableLarge").value(0))
            .andExpect(jsonPath("$[1].code").value("ST-FAR"))
            .andExpect(jsonPath("$[1].availableLarge").value(1));
    }

    @Test
    @DisplayName("deve devolver lista vazia quando a morada não é encontrada")
    void testSearchUnknownAddress() throws Exception {
        mockMvc.perform(get("/api/stations/search").param("address", "morada inexistente"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }
}
