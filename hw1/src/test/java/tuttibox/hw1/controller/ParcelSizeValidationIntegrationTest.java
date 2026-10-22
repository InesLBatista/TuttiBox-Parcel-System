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

import tuttibox.hw1.TestcontainersConfiguration;

import tools.jackson.databind.ObjectMapper;
import tuttibox.hw1.dto.DropOffRequestDto;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.repository.ParcelRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest 
@AutoConfigureMockMvc 
@Transactional 
@Import (TestcontainersConfiguration.class)
public class ParcelSizeValidationIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ParcelRepository parcelRepository;

    @BeforeEach
    void setUp() {
        parcelRepository.deleteAll();
    }

    @Test 
    @DisplayName ("deve rejeitar o dropoff quando o compartimento é demasiado pequeno para a encomenda")
    void testCompartmentTooSmallReturnsBadRequest() throws Exception {
        Parcel parcel = new Parcel("TRK-SIZE-01", "client@example.com", "L");
        parcelRepository.save(parcel);

        DropOffRequestDto dropOffDto = new DropOffRequestDto();
        dropOffDto.setStationId("STATION_01");
        dropOffDto.setCompartmentId("COMP_S1"); 
        dropOffDto.setCourierId("COURIER_01");

        mockMvc.perform(post("/api/parcels/TRK-SIZE-01/dropoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dropOffDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("tamanho do compartimento S é muito pequeno para o tamanho da encomenda L"));
    }

    @Test
    @DisplayName("deve rejeitar o dropoff quando o id do compartimento não especifica um tamanho válido")
    void testInvalidCompartmentIdFormatReturnsBadRequest() throws Exception {
        Parcel parcel = new Parcel("TRK-SIZE-02", "client@example.com", "M");
        parcelRepository.save(parcel);

        DropOffRequestDto dropOffDto = new DropOffRequestDto();
        dropOffDto.setStationId("STATION_01");
        dropOffDto.setCompartmentId("COMP_123"); 
        dropOffDto.setCourierId("COURIER_01");

        mockMvc.perform(post("/api/parcels/TRK-SIZE-02/dropoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dropOffDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("compartimento não especifica um tamanho válido: COMP_123"));
    }

    @Test
    @DisplayName("deve permitir o dropoff quando o tamanho do compartimento é compatível")
    void testCompatibleSizeSInMRectsSuccess() throws Exception {
        Parcel parcel = new Parcel("TRK-SIZE-03", "client@example.com", "S");
        parcelRepository.save(parcel);

        DropOffRequestDto dropOffDto = new DropOffRequestDto();
        dropOffDto.setStationId("STATION_01");
        dropOffDto.setCompartmentId("COMP_M1"); 
        dropOffDto.setCourierId("COURIER_01");

        mockMvc.perform(post("/api/parcels/TRK-SIZE-03/dropoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dropOffDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState").value("READY_FOR_PICKUP"));
    }
}
