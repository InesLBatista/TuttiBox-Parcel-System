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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class GlobalExceptionHandlerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ParcelRepository parcelRepository;

    @BeforeEach
    void setUp() {
        parcelRepository.deleteAll();
    }

    @Test
    @DisplayName("should return 400 when trying an invalid state transition")
    void testInvalidTransitionReturns400() throws Exception {
        //encomenda criada, tento fazer pickup direto pelo que devo ver erro de transição inválida
        Parcel parcel = new Parcel("TRK-BAD-01", "client@example.com", "M");
        parcelRepository.save(parcel);

        mockMvc.perform(post("/api/parcels/TRK-BAD-01/cancel")
                .param("actor", "PARTNER"))
                .andExpect(status().isOk()); //sucesso no cancelamento

        //a tentar cancelar de novo o que já está cancelado, deve retornar 400
        mockMvc.perform(post("/api/parcels/TRK-BAD-01/cancel")
                .param("actor", "PARTNER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("should return 404 when parcel tracking code does not exist")
    void testParcelNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/parcels/NON-EXISTENT/history"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists());
    }
}
