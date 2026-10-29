package tuttibox.hw1.bdd;

import java.util.LinkedHashMap;
import java.util.Map;

import io.cucumber.java.en.When;
import tuttibox.hw1.dto.DropOffRequestDto;
import tuttibox.hw1.dto.ParcelRequestDto;
import tuttibox.hw1.dto.PickUpRequestDto;

public class ApiSteps {
    private final ApiSupport api;
    private final ScenarioState state;

    public ApiSteps(ApiSupport api, ScenarioState state) {
        this.api = api;
        this.state = state;
    }

    @When("I search for stations near {string}")
    public void iSearchForStationsNear(String address) throws Exception {
        api.get("/api/stations/search", Map.of("address", address));
    }

    @When("I register a parcel of size {string} for {string} from partner {string} at station {string}")
    public void iRegisterAParcel(
            String size, String recipientContact, String partnerId, String stationId) throws Exception {
        ParcelRequestDto request = new ParcelRequestDto();
        request.setStationId(stationId);
        request.setCompartmentSize(size);
        request.setRecipientContact(recipientContact);
        request.setPartnerId(partnerId);
        api.postJson("/api/parcels", request);
    }

    @When("I remember the last tracking code as {string}")
    public void iRememberTheLastTrackingCodeAs(String name) {
        state.rememberTrackingCode(name, state.getLastTrackingCode());
    }

    @When("I request drop-off for parcel {string} at station {string} compartment {string} by courier {string}")
    public void iRequestDropOff(
            String trackingCode, String stationId, String compartmentId, String courierId) throws Exception {
        api.postJson("/api/parcels/" + trackingCode + "/dropoff",
            dropOffRequest(stationId, compartmentId, courierId));
    }

    @When("I confirm drop-off for parcel {string} at station {string} compartment {string} by courier {string}")
    public void iConfirmDropOff(
            String trackingCode, String stationId, String compartmentId, String courierId) throws Exception {
        api.postJson("/api/parcels/" + trackingCode + "/dropoff/confirm",
            dropOffRequest(stationId, compartmentId, courierId));
    }

    @When("I pick up with code {string}")
    public void iPickUpWithCode(String pickupCode) throws Exception {
        PickUpRequestDto request = new PickUpRequestDto();
        request.setPickupCode(pickupCode);
        api.postJson("/api/parcels/pickup", request);
    }

    @When("I request the status of parcel {string}")
    public void iRequestTheStatusOfParcel(String trackingCode) throws Exception {
        api.get("/api/parcels/" + trackingCode + "/status", Map.of());
    }

    @When("I request the history of parcel {string}")
    public void iRequestTheHistoryOfParcel(String trackingCode) throws Exception {
        api.get("/api/parcels/" + trackingCode + "/history", Map.of());
    }

    @When("I request the details of parcel {string}")
    public void iRequestTheDetailsOfParcel(String trackingCode) throws Exception {
        api.get("/api/parcels/" + trackingCode, Map.of());
    }

    @When("I cancel parcel {string} by actor {string}")
    public void iCancelParcel(String trackingCode, String actor) throws Exception {
        api.post("/api/parcels/" + trackingCode + "/cancel", Map.of("actor", actor));
    }

    @When("I cancel the parcel remembered as {string} by actor {string}")
    public void iCancelRememberedParcel(String name, String actor) throws Exception {
        api.post("/api/parcels/" + state.rememberedTrackingCode(name) + "/cancel", Map.of("actor", actor));
    }

    @When("I expire parcel {string} by actor {string}")
    public void iExpireParcel(String trackingCode, String actor) throws Exception {
        api.post("/api/parcels/" + trackingCode + "/expire", Map.of("actor", actor));
    }

    @When("I return parcel {string} by actor {string}")
    public void iReturnParcel(String trackingCode, String actor) throws Exception {
        api.post("/api/parcels/" + trackingCode + "/return", Map.of("actor", actor));
    }

    @When("I list all parcels")
    public void iListAllParcels() throws Exception {
        api.get("/api/operator/parcels", Map.of());
    }

    @When("I list parcels for station {string}")
    public void iListParcelsForStation(String stationId) throws Exception {
        api.get("/api/operator/parcels", Map.of("stationId", stationId));
    }

    @When("I list parcels in state {string}")
    public void iListParcelsInState(String parcelState) throws Exception {
        api.get("/api/operator/parcels", Map.of("state", parcelState));
    }

    @When("I list expired parcels")
    public void iListExpiredParcels() throws Exception {
        api.get("/api/operator/parcels/expired", Map.of());
    }

    @When("I list expired parcels for station {string}")
    public void iListExpiredParcelsForStation(String stationId) throws Exception {
        api.get("/api/operator/parcels/expired", Map.of("stationId", stationId));
    }

    @When("I list compartments for station {string}")
    public void iListCompartmentsForStation(String stationId) throws Exception {
        api.get("/api/operator/stations/" + stationId + "/compartments", Map.of());
    }

    @When("I remove expired parcel {string} by operator {string}")
    public void iRemoveExpiredParcel(String trackingCode, String actor) throws Exception {
        api.post("/api/operator/parcels/" + trackingCode + "/remove", Map.of("actor", actor));
    }

    @When("I put compartment {string} at station {string} out of service by operator {string}")
    public void iPutCompartmentOutOfService(
            String compartmentId, String stationId, String actor) throws Exception {
        api.post("/api/operator/stations/" + stationId + "/compartments/" + compartmentId
            + "/out-of-service", Map.of("actor", actor));
    }

    @When("I put compartment {string} at station {string} in service by operator {string}")
    public void iPutCompartmentInService(
            String compartmentId, String stationId, String actor) throws Exception {
        api.post("/api/operator/stations/" + stationId + "/compartments/" + compartmentId
            + "/in-service", Map.of("actor", actor));
    }

    @When("I request page {string}")
    public void iRequestPage(String page) throws Exception {
        api.get("/" + page, Map.of());
    }

    private DropOffRequestDto dropOffRequest(String stationId, String compartmentId, String courierId) {
        DropOffRequestDto request = new DropOffRequestDto();
        request.setStationId(stationId);
        request.setCompartmentId(compartmentId);
        request.setCourierId(courierId);
        return request;
    }

    @SuppressWarnings("unused")
    private Map<String, String> params(String... keysAndValues) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            params.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return params;
    }
}
