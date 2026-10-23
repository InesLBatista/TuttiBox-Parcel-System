package tuttibox.hw1.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import tuttibox.hw1.TestcontainersConfiguration;
import tuttibox.hw1.fake.InMemoryGeocodingFake;
import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.CompartmentStatus;
import tuttibox.hw1.model.Station;
import tuttibox.hw1.port.GeocodingException;
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
    @Autowired private InMemoryGeocodingFake geocodingFake;

    @BeforeEach
    void setUp() {
        geocodingFake.clearFailure();
        compartmentRepository.deleteAll();
        stationRepository.deleteAll();

        Station near = stationRepository.save(new Station("ST-NEAR", "Estação Perto", 40.6305, -8.6571));
        Station far = stationRepository.save(new Station("ST-FAR", "Estação Longe", 38.7223, -9.1393));

        compartmentRepository.save(compartment("N-S1", near, CompartmentSize.S, CompartmentStatus.FREE));
        compartmentRepository.save(compartment("N-S2", near, CompartmentSize.S, CompartmentStatus.FREE));
        compartmentRepository.save(compartment("N-M1", near, CompartmentSize.M, CompartmentStatus.FREE));
        compartmentRepository.save(compartment("N-S3", near, CompartmentSize.S, CompartmentStatus.RESERVED));
        compartmentRepository.save(compartment("N-M2", near, CompartmentSize.M, CompartmentStatus.OUT_OF_SERVICE));
        compartmentRepository.save(compartment("N-L1", near, CompartmentSize.L, CompartmentStatus.OCCUPIED));
        compartmentRepository.save(compartment("F-L1", far, CompartmentSize.L, CompartmentStatus.FREE));
        compartmentRepository.save(compartment("F-S1", far, CompartmentSize.S, CompartmentStatus.OCCUPIED));
        compartmentRepository.save(compartment("F-M1", far, CompartmentSize.M, CompartmentStatus.RESERVED));
    }

    @Test
    @DisplayName("deve servir a página web do destinatário")
    void testRecipientPageIsServed() throws Exception {
        mockMvc.perform(get("/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(containsString("TuttiBox - Encontrar")))
            .andExpect(content().string(containsString("/api/stations/search")));
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
            .andExpect(jsonPath("$[1].availableSmall").value(0))
            .andExpect(jsonPath("$[1].availableMedium").value(0))
            .andExpect(jsonPath("$[1].availableLarge").value(1));
    }

    @Test
    @DisplayName("deve devolver lista vazia quando a morada não é encontrada")
    void testSearchUnknownAddress() throws Exception {
        mockMvc.perform(get("/api/stations/search").param("address", "morada inexistente"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("deve devolver 503 quando o geocoding está indisponível")
    void testGeocodingUnavailable() throws Exception {
        geocodingFake.setFailure(GeocodingException.Reason.UNAVAILABLE);

        mockMvc.perform(get("/api/stations/search").param("address", "Anadia"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.message").value("geocoding service is unavailable"));
    }

    @Test
    @DisplayName("deve devolver 504 quando o geocoding excede o tempo limite")
    void testGeocodingTimeout() throws Exception {
        geocodingFake.setFailure(GeocodingException.Reason.TIMEOUT);

        mockMvc.perform(get("/api/stations/search").param("address", "Anadia"))
            .andExpect(status().isGatewayTimeout())
            .andExpect(jsonPath("$.status").value(504))
            .andExpect(jsonPath("$.message").value("geocoding service timed out"));
    }

    @Test
    @DisplayName("deve devolver 503 quando o limite de utilização do geocoding é excedido")
    void testGeocodingRateLimited() throws Exception {
        geocodingFake.setFailure(GeocodingException.Reason.RATE_LIMITED);

        mockMvc.perform(get("/api/stations/search").param("address", "Anadia"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.message").value("geocoding service usage limit exceeded"));
    }

    @Test
    @DisplayName("deve devolver 502 quando o geocoding devolve uma resposta inválida")
    void testGeocodingInvalidResponse() throws Exception {
        geocodingFake.setFailure(GeocodingException.Reason.INVALID_RESPONSE);

        mockMvc.perform(get("/api/stations/search").param("address", "Anadia"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.status").value(502))
            .andExpect(jsonPath("$.message").value("geocoding service returned an invalid response"));
    }

    private Compartment compartment(
        String code, Station station, CompartmentSize size, CompartmentStatus status) {
        Compartment compartment = new Compartment(code, station, size);
        compartment.setStatus(status);
        return compartment;
    }
}
