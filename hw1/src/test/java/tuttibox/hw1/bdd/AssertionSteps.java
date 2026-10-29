package tuttibox.hw1.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import tools.jackson.databind.JsonNode;
import tuttibox.hw1.fake.InMemoryNotificationFake;
import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentStatus;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.service.PickupCode;

public class AssertionSteps {
    private final ApiSupport api;
    private final ScenarioState state;
    private final TestDataSupport data;
    private final CompartmentRepository compartmentRepository;

    public AssertionSteps(
            ApiSupport api,
            ScenarioState state,
            TestDataSupport data,
            CompartmentRepository compartmentRepository) {
        this.api = api;
        this.state = state;
        this.data = data;
        this.compartmentRepository = compartmentRepository;
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(int expected) {
        assertThat(api.status()).isEqualTo(expected);
    }

    @Then("the response is a JSON array with {int} entries")
    public void theResponseIsAJsonArrayWithEntries(int expected) {
        assertThat(api.arraySize()).isEqualTo(expected);
    }

    @Then("the response is an empty JSON array")
    public void theResponseIsAnEmptyJsonArray() {
        assertThat(api.arraySize()).isZero();
    }

    @Then("the response field {string} is {string}")
    public void theResponseFieldIs(String field, String expected) {
        JsonNode value = api.json().get(field);
        assertThat(value).as("response field %s", field).isNotNull();
        assertThat(value.asString()).isEqualTo(expected);
    }

    @Then("the response does not contain field {string}")
    public void theResponseDoesNotContainField(String field) {
        assertThat(api.json().has(field)).isFalse();
    }

    @Then("the response body is {string}")
    public void theResponseBodyIs(String expected) {
        assertThat(api.body()).isEqualTo(expected);
    }

    @Then("the error message is {string}")
    public void theErrorMessageIs(String expected) {
        assertThat(api.text("message")).isEqualTo(expected);
    }

    @Then("the error message contains {string}")
    public void theErrorMessageContains(String expected) {
        assertThat(api.text("message")).contains(expected);
    }

    @Then("the response contains {string}")
    public void theResponseContains(String expected) {
        assertThat(api.body()).contains(expected);
    }

    @Then("the response content type starts with {string}")
    public void theResponseContentTypeStartsWith(String expected) {
        assertThat(api.contentType()).startsWith(expected);
    }

    @Then("the response contains a tracking code")
    public void theResponseContainsATrackingCode() {
        assertThat(api.json().get("trackingCode").asString()).isNotBlank();
    }

    @Then("the assigned compartment of the last parcel is {string}")
    public void theAssignedCompartmentOfTheLastParcelIs(String expected) {
        assertThat(api.json().get("assignedCompartmentCode").asString()).isEqualTo(expected);
    }

    @Then("the assigned station of the last parcel is {string}")
    public void theAssignedStationOfTheLastParcelIs(String expected) {
        assertThat(api.json().get("assignedStationCode").asString()).isEqualTo(expected);
    }

    @Then("the last parcel has state {string}")
    public void theLastParcelHasState(String expected) {
        assertThat(api.json().get("currentState").asString()).isEqualTo(expected);
    }

    @Then("the two registrations produced different tracking codes")
    public void theTwoRegistrationsProducedDifferentTrackingCodes() {
        assertThat(state.getTrackingCodes()).hasSize(2).doesNotHaveDuplicates();
    }

    @Then("station {string} is at position {int}")
    public void stationIsAtPosition(String code, int position) {
        assertThat(api.json().get(position).get("code").asString()).isEqualTo(code);
    }

    @Then("station {string} has {int} small, {int} medium and {int} large compartments available")
    public void stationHasAvailability(String code, int small, int medium, int large) {
        JsonNode station = findArrayEntry("code", code);
        assertThat(station.get("availableSmall").asInt()).isEqualTo(small);
        assertThat(station.get("availableMedium").asInt()).isEqualTo(medium);
        assertThat(station.get("availableLarge").asInt()).isEqualTo(large);
    }

    @Then("the history contains a transition from {string} to {string} by {string}")
    public void theHistoryContainsATransition(String fromState, String toState, String actor) {
        assertThat(api.json()).isNotNull();
        boolean found = false;
        for (JsonNode transition : api.json()) {
            JsonNode actualFrom = transition.get("fromState");
            String actualFromState = actualFrom == null || actualFrom.isNull() ? null : actualFrom.asString();
            if (java.util.Objects.equals(actualFromState, fromState)
                    && toState.equals(transition.get("toState").asString())
                    && actor.equals(transition.get("actor").asString())) {
                found = true;
                break;
            }
        }
        assertThat(found).as("history must contain %s -> %s by %s", fromState, toState, actor).isTrue();
    }

    @Then("the history has {int} transitions")
    public void theHistoryHasTransitions(int expected) {
        assertThat(api.arraySize()).isEqualTo(expected);
    }

    @Then("the parcel {string} has state {string}")
    public void theParcelHasState(String trackingCode, String expected) {
        assertThat(data.requireParcel(trackingCode).getCurrentState().name()).isEqualTo(expected);
    }

    @Then("the parcel remembered as {string} has state {string}")
    public void theRememberedParcelHasState(String name, String expected) {
        assertThat(data.requireParcel(state.rememberedTrackingCode(name)).getCurrentState().name())
            .isEqualTo(expected);
    }

    @Then("the parcel {string} has no pickup code hash")
    public void theParcelHasNoPickupCodeHash(String trackingCode) {
        assertThat(data.requireParcel(trackingCode).getPickupCodeHash()).isNull();
    }

    @Then("the parcel {string} has a pickup code hash")
    public void theParcelHasAPickupCodeHash(String trackingCode) {
        assertThat(data.requireParcel(trackingCode).getPickupCodeHash()).isNotBlank();
    }

    @Then("the compartment {string} at station {string} has status {string}")
    @Then("compartment {string} at station {string} has status {string}")
    public void theCompartmentHasStatus(String compartmentCode, String stationCode, String expected) {
        Compartment compartment = compartmentRepository
            .findByStationCodeAndCode(stationCode, compartmentCode)
            .orElseThrow();
        assertThat(compartment.getStatus()).isEqualTo(CompartmentStatus.valueOf(expected));
    }

    @Then("the compartment {string} at station {string} is open")
    public void theCompartmentIsOpen(String compartmentCode, String stationCode) {
        assertThat(data.isDoorClosed(stationCode, compartmentCode)).isFalse();
    }

    @Then("the compartment {string} at station {string} is closed")
    public void theCompartmentIsClosed(String compartmentCode, String stationCode) {
        assertThat(data.isDoorClosed(stationCode, compartmentCode)).isTrue();
    }

    @Then("a pickup code notification was sent to {string} for parcel {string}")
    public void aPickupCodeNotificationWasSentTo(String recipientContact, String trackingCode) {
        InMemoryNotificationFake.Notification notification = data.notifications().stream()
            .filter(candidate -> candidate.trackingCode().equals(trackingCode))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no notification for " + trackingCode));
        Parcel parcel = data.requireParcel(trackingCode);
        assertThat(notification.recipientContact()).isEqualTo(recipientContact);
        assertThat(notification.pickupCode()).matches("[A-HJ-NP-Z2-9]{8}");
        assertThat(PickupCode.matches(notification.pickupCode(), parcel.getPickupCodeHash())).isTrue();
    }

    @Then("no pickup code notification was sent")
    public void noPickupCodeNotificationWasSent() {
        assertThat(data.notifications()).isEmpty();
    }

    @Then("the response array contains tracking code {string}")
    public void theResponseArrayContainsTrackingCode(String trackingCode) {
        assertThat(findArrayEntry("trackingCode", trackingCode)).isNotNull();
    }

    @Then("the response array does not contain tracking code {string}")
    public void theResponseArrayDoesNotContainTrackingCode(String trackingCode) {
        for (JsonNode entry : api.json()) {
            assertThat(entry.get("trackingCode").asString()).isNotEqualTo(trackingCode);
        }
    }

    @Then("the response array contains compartment {string} with status {string}")
    public void theResponseArrayContainsCompartmentWithStatus(String code, String expectedStatus) {
        JsonNode compartment = findArrayEntry("code", code);
        assertThat(compartment.get("status").asString()).isEqualTo(expectedStatus);
    }

    @Then("the system contains {int} parcels")
    @Then("the system contains {int} parcel")
    public void theSystemContainsParcels(int expected) {
        assertThat(data.parcelCount()).isEqualTo(expected);
    }

    private JsonNode findArrayEntry(String field, String value) {
        assertThat(api.json()).isNotNull();
        for (JsonNode entry : api.json()) {
            JsonNode candidate = entry.get(field);
            if (candidate != null && value.equals(candidate.asString())) {
                return entry;
            }
        }
        throw new AssertionError("no array entry with " + field + "=" + value);
    }
}
