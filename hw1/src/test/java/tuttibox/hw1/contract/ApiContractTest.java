package tuttibox.hw1.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tuttibox.hw1.TestcontainersConfiguration;
import tuttibox.hw1.fake.InMemoryGeocodingFake;
import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.Coordinates;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.Station;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.ParcelRepository;
import tuttibox.hw1.repository.StationRepository;
import tuttibox.hw1.service.PickupCode;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class ApiContractTest {
    private static final SchemaRegistry SCHEMA_REGISTRY =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private CompartmentRepository compartmentRepository;
    @Autowired
    private ParcelRepository parcelRepository;
    @Autowired
    private InMemoryGeocodingFake geocodingFake;

    @BeforeEach
    void setUp() {
        parcelRepository.deleteAll();
        compartmentRepository.deleteAll();
        stationRepository.deleteAll();
        geocodingFake.clearFailure();
    }

    @Test
    @DisplayName("contrato: registo devolve ParcelResponseDto")
    void registerParcelResponseMatchesContract() throws Exception {
        Station station = station("ST-REG");
        freeCompartment("C-S1", station, CompartmentSize.S);
        freeCompartment("C-M1", station, CompartmentSize.M);

        MvcResult result = mockMvc.perform(post("/api/parcels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                    "stationId", "ST-REG",
                    "compartmentSize", "M",
                    "recipientContact", "client@example.com",
                    "partnerId", "P-01"))))
            .andExpect(status().isCreated())
            .andReturn();

        assertMatches(result, "parcel-response.schema.json");
    }

    @Test
    @DisplayName("contrato: pesquisa devolve lista de NearbyStationDto")
    void stationSearchResponseMatchesContract() throws Exception {
        Station near = station("ST-NEAR");
        Station far = station("ST-FAR");
        freeCompartment("C-S1", near, CompartmentSize.S);
        freeCompartment("C-L1", far, CompartmentSize.L);
        geocodingFake.addAddress("Contrato Aveiro", new Coordinates(40.6405, -8.6538));

        MvcResult result = mockMvc.perform(get("/api/stations/search")
                .param("address", "Contrato Aveiro"))
            .andExpect(status().isOk())
            .andReturn();

        assertArrayMatches(result, "nearby-station.schema.json");
    }

    @Test
    @DisplayName("contrato: drop-off e confirmação devolvem a entidade Parcel")
    void dropOffResponseMatchesContract() throws Exception {
        Station station = station("ST-DROP");
        Compartment compartment = reservedCompartment("C-M1", station, CompartmentSize.M);
        parcel("TRK-DROP0001", station, compartment, CompartmentSize.M);

        MvcResult dropOff = mockMvc.perform(post("/api/parcels/TRK-DROP0001/dropoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(dropOffJson("ST-DROP", "C-M1")))
            .andExpect(status().isOk())
            .andReturn();
        assertMatches(dropOff, "parcel.schema.json");

        MvcResult confirm = mockMvc.perform(post("/api/parcels/TRK-DROP0001/dropoff/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(dropOffJson("ST-DROP", "C-M1")))
            .andExpect(status().isOk())
            .andReturn();
        assertMatches(confirm, "parcel.schema.json");
    }

    @Test
    @DisplayName("contrato: pickup devolve a entidade Parcel sem expor o hash")
    void pickupResponseMatchesContract() throws Exception {
        Station station = station("ST-PICK");
        Compartment compartment = occupiedCompartment("C-M1", station, CompartmentSize.M);
        readyParcel("TRK-PICK0001", station, compartment, CompartmentSize.M, "ABCD2345");

        MvcResult result = mockMvc.perform(post("/api/parcels/pickup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("pickupCode", "ABCD2345"))))
            .andExpect(status().isOk())
            .andReturn();

        assertMatches(result, "parcel.schema.json");
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).has("pickupCodeHash"))
            .isFalse();
    }

    @Test
    @DisplayName("contrato: histórico devolve lista de transições")
    void historyResponseMatchesContract() throws Exception {
        Station station = station("ST-HIST");
        Compartment compartment = occupiedCompartment("C-M1", station, CompartmentSize.M);
        readyParcel("TRK-HIST0001", station, compartment, CompartmentSize.M, "ABCD2345");

        MvcResult result = mockMvc.perform(get("/api/parcels/TRK-HIST0001/history"))
            .andExpect(status().isOk())
            .andReturn();

        assertArrayMatches(result, "parcel-transition.schema.json");
    }

    @Test
    @DisplayName("contrato: estado devolve um valor de ParcelState")
    void parcelStatusResponseMatchesContract() throws Exception {
        Station station = station("ST-STATUS");
        Compartment compartment = reservedCompartment("C-M1", station, CompartmentSize.M);
        parcel("TRK-STAT0001", station, compartment, CompartmentSize.M);

        MvcResult result = mockMvc.perform(get("/api/parcels/TRK-STAT0001/status"))
            .andExpect(status().isOk())
            .andReturn();

        assertMatches(result, "parcel-state.schema.json");
    }

    @Test
    @DisplayName("contrato: detalhe da encomenda devolve a entidade Parcel")
    void parcelDetailsResponseMatchesContract() throws Exception {
        Station station = station("ST-DETAIL");
        Compartment compartment = reservedCompartment("C-M1", station, CompartmentSize.M);
        parcel("TRK-DETAIL01", station, compartment, CompartmentSize.M);

        MvcResult result = mockMvc.perform(get("/api/parcels/TRK-DETAIL01"))
            .andExpect(status().isOk())
            .andReturn();

        assertMatches(result, "parcel.schema.json");
    }

    @Test
    @DisplayName("contrato: listagem do operador devolve ParcelResponseDto")
    void operatorParcelListMatchesContract() throws Exception {
        Station station = station("ST-OP");
        Compartment first = occupiedCompartment("C-M1", station, CompartmentSize.M);
        readyParcel("TRK-OP000001", station, first, CompartmentSize.M, "ABCD2345");
        Compartment second = reservedCompartment("C-S1", station, CompartmentSize.S);
        parcel("TRK-OP000002", station, second, CompartmentSize.S);

        MvcResult result = mockMvc.perform(get("/api/operator/parcels"))
            .andExpect(status().isOk())
            .andReturn();

        assertArrayMatches(result, "parcel-response.schema.json");
    }

    @Test
    @DisplayName("contrato: listagem de compartimentos devolve CompartmentResponseDto")
    void compartmentListMatchesContract() throws Exception {
        Station station = station("ST-COMP");
        freeCompartment("C-S1", station, CompartmentSize.S);
        Compartment outOfService = freeCompartment("C-M1", station, CompartmentSize.M);
        outOfService.putOutOfService();
        compartmentRepository.save(outOfService);

        MvcResult result = mockMvc.perform(get("/api/operator/stations/ST-COMP/compartments"))
            .andExpect(status().isOk())
            .andReturn();

        assertArrayMatches(result, "compartment-response.schema.json");
    }

    @Test
    @DisplayName("contrato: erros de domínio devolvem ErrorResponse")
    void errorResponseMatchesContract() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/parcels/TRK-MISSING/history"))
            .andExpect(status().isNotFound())
            .andReturn();

        assertMatches(result, "error-response.schema.json");
    }

    @Test
    @DisplayName("contrato: erros de validação devolvem ErrorResponse")
    void validationErrorResponseMatchesContract() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/parcels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                    "stationId", "",
                    "compartmentSize", "X",
                    "recipientContact", "",
                    "partnerId", ""))))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertMatches(result, "error-response.schema.json");
    }

    private void assertMatches(MvcResult result, String schemaName) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        List<com.networknt.schema.Error> errors = schema(schemaName).validate(body);
        if (!errors.isEmpty()) {
            fail("response does not match " + schemaName + ": " + errors);
        }
    }

    private void assertArrayMatches(MvcResult result, String schemaName) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.isArray()).as("response must be a JSON array").isTrue();
        Schema schema = schema(schemaName);
        for (JsonNode item : body) {
            List<com.networknt.schema.Error> errors = schema.validate(item);
            if (!errors.isEmpty()) {
                fail("array item does not match " + schemaName + ": " + errors);
            }
        }
    }

    private Schema schema(String name) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/contracts/" + name)) {
            assertThat(input).as("schema resource %s", name).isNotNull();
            return SCHEMA_REGISTRY.getSchema(input);
        }
    }

    private String dropOffJson(String stationId, String compartmentId) throws Exception {
        return json(Map.of(
            "stationId", stationId,
            "compartmentId", compartmentId,
            "courierId", "COURIER-01"));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private Station station(String code) {
        return stationRepository.save(new Station(code, "Estação " + code, 40.6305, -8.6571));
    }

    private Compartment freeCompartment(String code, Station station, CompartmentSize size) {
        return compartmentRepository.save(new Compartment(code, station, size));
    }

    private Compartment reservedCompartment(String code, Station station, CompartmentSize size) {
        Compartment compartment = freeCompartment(code, station, size);
        compartment.reserve();
        return compartmentRepository.save(compartment);
    }

    private Compartment occupiedCompartment(String code, Station station, CompartmentSize size) {
        Compartment compartment = reservedCompartment(code, station, size);
        compartment.occupy();
        return compartmentRepository.save(compartment);
    }

    private Parcel parcel(String trackingCode, Station station, Compartment compartment, CompartmentSize size) {
        Parcel parcel = new Parcel(trackingCode, "client@example.com", size.name());
        parcel.setStation(station);
        parcel.setAssignedCompartment(compartment);
        parcel.setPartnerId("P-01");
        parcel.setRegisteredAt(LocalDateTime.now());
        parcel.setDropOffDeadline(LocalDateTime.now().plusDays(2));
        return parcelRepository.save(parcel);
    }

    private Parcel readyParcel(
            String trackingCode,
            Station station,
            Compartment compartment,
            CompartmentSize size,
            String pickupCode) {
        Parcel parcel = parcel(trackingCode, station, compartment, size);
        parcel.markAsReadyForPickup("COURIER-01", LocalDateTime.now().plusDays(7));
        parcel.issuePickupCode(PickupCode.hash(pickupCode));
        return parcelRepository.save(parcel);
    }
}
