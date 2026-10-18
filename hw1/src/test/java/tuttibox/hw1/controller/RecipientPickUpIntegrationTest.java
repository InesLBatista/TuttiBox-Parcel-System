package tuttibox.hw1.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import tuttibox.hw1.dto.PickUpRequestDto;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelState;
import tuttibox.hw1.repository.ParcelRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest 
@AutoConfigureMockMvc 
@Transactional 
public class RecipientPickUpIntegrationTest {
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
    @DisplayName ("recipient can successfully pick up the parcel and transition state into PICKED_UP")
    void testRecipientPickUpApi() throws Exception {
        // Given: Insere uma encomenda no estado READY_FOR_PICKUP
        Parcel parcel = new Parcel("TRK-PICKUP-123", "client@example.com", "M");
        parcel.markAsReadyForPickup("COURIER_01");
        parcelRepository.save(parcel);

        PickUpRequestDto pickUpDto = new PickUpRequestDto();
        pickUpDto.setStationId("STATION_01");
        pickUpDto.setCompartmentId("COMP_A1");
        pickUpDto.setRecipientId("CLIENT_123");

        // When & Then: Executa o pedido POST para o endpoint de pickup
        mockMvc.perform(post("/api/parcels/TRK-PICKUP-123/pickup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pickUpDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingCode").value("TRK-PICKUP-123"))
                .andExpect(jsonPath("$.currentState").value("PICKED_UP"));

        // Validação na BD
        Parcel updatedParcel = parcelRepository.findByTrackingCode("TRK-PICKUP-123").orElseThrow();
        assertThat(updatedParcel.getCurrentState()).isEqualTo(ParcelState.PICKED_UP);
    }
}
