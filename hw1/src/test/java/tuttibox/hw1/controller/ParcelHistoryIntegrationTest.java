package tuttibox.hw1.controller;

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
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.repository.ParcelRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest 
@AutoConfigureMockMvc 
@Transactional 
@Import(TestcontainersConfiguration.class)
public class ParcelHistoryIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ParcelRepository parcelRepository;

    @BeforeEach
    void setUp() {
        parcelRepository.deleteAll();
    }

    @Test
    @DisplayName("deve obter com sucesso o histórico de transições de estado da encomenda")
    void testGetParcelHistory() throws Exception {
        Parcel parcel = new Parcel("TRK-HIST-01", "user@example.com", "S");
        parcel.markAsReadyForPickup("COURIER_01");
        parcelRepository.save(parcel);

        mockMvc.perform(get("/api/parcels/TRK-HIST-01/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[1].toState").value("READY_FOR_PICKUP"));
    }
}
