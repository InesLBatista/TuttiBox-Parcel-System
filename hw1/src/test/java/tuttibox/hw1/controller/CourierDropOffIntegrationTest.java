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
@Import(TestcontainersConfiguration.class)
public class CourierDropOffIntegrationTest {
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
    @DisplayName ("courier consegue fazer dropoff de uma encomenda e transitar para READY_FOR_PICKUP")
    void testCourierDropOffApi() throws Exception {
        Parcel parcel = new Parcel("TRK-TEST123", "client@example.com", "M");
        parcelRepository.save(parcel);

        DropOffRequestDto dropOffDto = new DropOffRequestDto();
        dropOffDto.setStationId("STATION_01");
        dropOffDto.setCompartmentId("COMP_M1");
        dropOffDto.setCourierId("COURIER_123");

        //execução do pedido post para endpoint de dropoff
        mockMvc.perform(post("/api/parcels/TRK-TEST123/dropoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dropOffDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingCode").value("TRK-TEST123"))
                .andExpect(jsonPath("$.currentState").value("READY_FOR_PICKUP"));

        //estado deve mudar também na bd
        Parcel updatedParcel = parcelRepository.findByTrackingCode("TRK-TEST123").orElseThrow();
        assertThat(updatedParcel.getCurrentState()).isEqualTo(ParcelState.READY_FOR_PICKUP);
    }

    @Test
    @DisplayName("dropoff de encomenda inexistente devolve 404")
    void testDropOffUnknownParcelReturnsNotFound() throws Exception {
        DropOffRequestDto dropOffDto = new DropOffRequestDto();
        dropOffDto.setStationId("STATION_01");
        dropOffDto.setCompartmentId("COMP_M1");
        dropOffDto.setCourierId("COURIER_123");

        mockMvc.perform(post("/api/parcels/TRK-DOES-NOT-EXIST/dropoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dropOffDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
