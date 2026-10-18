package tuttibox.hw1.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import tuttibox.hw1.TestcontainersConfiguration;
import tuttibox.hw1.dto.ParcelRequestDto;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest 
@AutoConfigureMockMvc 
@Import(TestcontainersConfiguration.class)
public class ParcelControllerIntegrationTest {
    @Autowired 
    private MockMvc mockMvc;

    @Autowired 
    private ObjectMapper objectMapper;

    @Test 
    @DisplayName ("parter can register a parcel via rest api and receive a tracking code")
    void testRegisterParcelApi() throws Exception {
        //prepara os dados enviados
        ParcelRequestDto requestDto = new ParcelRequestDto();
        requestDto.setStationId("STATION_01");
        requestDto.setRecipientContact("destinatario@example.com");
        requestDto.setCompartmentSize("M");
        requestDto.setPartnerId("PARTNER_XYZ");

        //executa o pedido post para o endpoint rest
        mockMvc.perform(post("/api/parcels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated()) //para validar o http 201 created
                .andExpect(jsonPath("$.trackingCode").exists()) //garantir que o código foi de facto gerado
                .andExpect(jsonPath("$.currentState").value("CREATED")) //valida o estado inicial da máquina de estados, ou seja, CREATED
                .andExpect(jsonPath("$.compartmentSize").value("M"));
    }
}
