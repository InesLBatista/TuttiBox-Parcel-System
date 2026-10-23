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
import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.Station;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.ParcelRepository;
import tuttibox.hw1.repository.StationRepository;

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
    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private CompartmentRepository compartmentRepository;

    @BeforeEach
    void setUp() {
        parcelRepository.deleteAll();
    }

    @Test 
    @DisplayName ("deve rejeitar o dropoff quando o compartimento atribuído é demasiado pequeno")
    void testCompartmentTooSmallReturnsBadRequest() throws Exception {
        saveParcelWithCompartment("TRK-SIZE-01", "L", "COMP_S1", CompartmentSize.S);

        mockMvc.perform(post("/api/parcels/TRK-SIZE-01/dropoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dropOffDto("COMP_S1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("tamanho do compartimento S é muito pequeno para o tamanho da encomenda L"));
    }

    @Test
    @DisplayName("deve usar o tamanho do compartimento atribuído e não inferi-lo do texto do compartmentId")
    void testAssignedCompartmentSizeIsUsedInsteadOfCompartmentIdText() throws Exception {
        saveParcelWithCompartment("TRK-SIZE-02", "S", "COMP_123", CompartmentSize.M);

        mockMvc.perform(post("/api/parcels/TRK-SIZE-02/dropoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dropOffDto("COMP_123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState").value("CREATED"));
    }

    @Test 
    @DisplayName ("deve permitir abrir o compartimento quando o tamanho é compatível")
    void testCompatibleSizeSInMReturnsSuccess() throws Exception {
        saveParcelWithCompartment("TRK-SIZE-03", "S", "COMP_M1", CompartmentSize.M);

        mockMvc.perform(post("/api/parcels/TRK-SIZE-03/dropoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dropOffDto("COMP_M1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState").value("CREATED"));
    }

    private Parcel saveParcelWithCompartment(
            String trackingCode, String parcelSize, String compartmentCode, CompartmentSize compartmentSize) {
        Station station = stationRepository.save(
            new Station("STATION_01", "Estação Central", 40.63, -8.65));
        Compartment compartment = new Compartment(compartmentCode, station, compartmentSize);
        compartment.reserve();
        compartmentRepository.save(compartment);
        Parcel parcel = new Parcel(trackingCode, "client@example.com", parcelSize);
        parcel.setStation(station);
        parcel.setAssignedCompartment(compartment);
        return parcelRepository.save(parcel);
    }

    private DropOffRequestDto dropOffDto(String compartmentId) {
        DropOffRequestDto dropOffDto = new DropOffRequestDto();
        dropOffDto.setStationId("STATION_01");
        dropOffDto.setCompartmentId(compartmentId);
        dropOffDto.setCourierId("COURIER_01");
        return dropOffDto;
    }
}
