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
import tuttibox.hw1.fake.InMemoryHardwareFake;
import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.Station;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.ParcelRepository;
import tuttibox.hw1.repository.StationRepository;

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
    private ObjectMapper objectMapper;
    @Autowired
    private ParcelRepository parcelRepository;
    @Autowired
    private InMemoryHardwareFake hardwareFake;
    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private CompartmentRepository compartmentRepository;

    @BeforeEach
    void setUp() {
        parcelRepository.deleteAll();
        hardwareFake.setInjectFailure(false);
        hardwareFake.setInjectCloseFailure(false);
        hardwareFake.setInjectDoorNotClosed(false);
    }

    @Test
    @DisplayName("deve devolver 400 ao tentar uma transição de estado inválida")
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
    @DisplayName("deve devolver 404 quando o tracking code da encomenda não existe")
    void testParcelNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/parcels/NON-EXISTENT/history"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("deve devolver 503 quando o hardware falha durante o dropoff")
    void testHardwareFailureReturns503() throws Exception {
        Station station = stationRepository.save(
            new Station("STATION_01", "Estação Central", 40.63, -8.65));
        Compartment compartment = new Compartment("COMP_S1", station, CompartmentSize.S);
        compartment.reserve();
        compartmentRepository.save(compartment);
        Parcel parcel = new Parcel("TRK-HW-01", "client@example.com", "S");
        parcel.setStation(station);
        parcel.setAssignedCompartment(compartment);
        parcelRepository.save(parcel);
        hardwareFake.setInjectFailure(true);

        DropOffRequestDto dropOffDto = new DropOffRequestDto();
        dropOffDto.setStationId("STATION_01");
        dropOffDto.setCompartmentId("COMP_S1");
        dropOffDto.setCourierId("COURIER_01");

        mockMvc.perform(post("/api/parcels/TRK-HW-01/dropoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dropOffDto)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").exists());
    }
}
