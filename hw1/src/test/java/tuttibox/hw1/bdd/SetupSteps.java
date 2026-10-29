package tuttibox.hw1.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import tuttibox.hw1.exception.NoAvailableCompartmentException;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.CompartmentStatus;
import tuttibox.hw1.model.ParcelState;

public class SetupSteps {
    private final TestDataSupport data;
    private final ScenarioState state;

    public SetupSteps(TestDataSupport data, ScenarioState state) {
        this.data = data;
        this.state = state;
    }

    @Given("the system is clean")
    public void theSystemIsClean() {
        //the Cucumber hook cleans the database and resets the fakes before every scenario.
    }

    @Given("a station {string} named {string} at {double}, {double}")
    public void aStation(String code, String name, double latitude, double longitude) {
        data.station(code, name, latitude, longitude);
    }

    @Given("a free compartment {string} at station {string} of size {string}")
    public void aFreeCompartment(String code, String stationCode, String size) {
        data.compartment(code, stationCode, size(size), CompartmentStatus.FREE);
    }

    @Given("a reserved compartment {string} at station {string} of size {string}")
    public void aReservedCompartment(String code, String stationCode, String size) {
        data.compartment(code, stationCode, size(size), CompartmentStatus.RESERVED);
    }

    @Given("an occupied compartment {string} at station {string} of size {string}")
    public void anOccupiedCompartment(String code, String stationCode, String size) {
        data.compartment(code, stationCode, size(size), CompartmentStatus.OCCUPIED);
    }

    @Given("an out-of-service compartment {string} at station {string} of size {string}")
    public void anOutOfServiceCompartment(String code, String stationCode, String size) {
        data.compartment(code, stationCode, size(size), CompartmentStatus.OUT_OF_SERVICE);
    }

    @Given("a parcel {string} for {string} with size {string} at station {string} in compartment {string} in state {string}")
    public void aParcel(
            String trackingCode,
            String recipientContact,
            String size,
            String stationCode,
            String compartmentCode,
            String parcelState) {
        data.parcel(
            trackingCode, recipientContact, size(size), stationCode, compartmentCode,
            ParcelState.valueOf(parcelState));
    }

    @Given("a ready parcel {string} for {string} with size {string} at station {string} in compartment {string} with pickup code {string}")
    public void aReadyParcel(
            String trackingCode,
            String recipientContact,
            String size,
            String stationCode,
            String compartmentCode,
            String pickupCode) {
        data.readyParcel(
            trackingCode, recipientContact, size(size), stationCode, compartmentCode, pickupCode);
        state.setLastPickupCode(pickupCode);
    }

    @Given("the parcel {string} is expired")
    public void theParcelIsExpired(String trackingCode) {
        data.expireParcel(trackingCode);
    }

    @Given("the parcel {string} drop-off deadline is {string}")
    public void theParcelDropOffDeadlineIs(String trackingCode, String relative) {
        data.setDropOffDeadline(trackingCode, relative);
    }

    @Given("the parcel {string} pickup deadline is {string}")
    public void theParcelPickupDeadlineIs(String trackingCode, String relative) {
        data.setPickupDeadline(trackingCode, relative);
    }

    @Given("the address {string} resolves to {double}, {double}")
    public void theAddressResolvesTo(String address, double latitude, double longitude) {
        data.resolveAddress(address, latitude, longitude);
    }

    @Given("the geocoding provider will fail with {string}")
    public void theGeocodingProviderWillFailWith(String failure) {
        data.failGeocoding(failure);
    }

    @Given("the hardware will fail to open compartments")
    public void theHardwareWillFailToOpenCompartments() {
        data.hardwareFailsToOpen();
    }

    @Given("the hardware will fail to confirm closing")
    public void theHardwareWillFailToConfirmClosing() {
        data.hardwareFailsToClose();
    }

    @Given("the hardware will report the door as still open")
    public void theHardwareWillReportTheDoorAsStillOpen() {
        data.hardwareReportsDoorOpen();
    }

    @When("the hardware recovers")
    public void theHardwareRecovers() {
        data.clearHardwareFailures();
    }

    @Given("the seed data has been applied twice")
    public void theSeedDataHasBeenAppliedTwice() {
        data.seedTwice();
    }

    @When("the system applies deadlines")
    public void theSystemAppliesDeadlines() {
        data.applyDeadlines();
    }

    @When("the deadline scheduler runs")
    public void theDeadlineSchedulerRuns() {
        data.runScheduler();
    }

    @When("two partners register size {string} at station {string} concurrently")
    public void twoPartnersRegisterSizeConcurrently(String size, String stationCode) throws Exception {
        List<ScenarioState.ConcurrentRegistration> registrations =
            data.registerConcurrently(size(size), stationCode);
        state.setConcurrentRegistrations(registrations);
    }

    @Then("exactly one registration succeeds")
    public void exactlyOneRegistrationSucceeds() {
        assertThat(state.getConcurrentRegistrations().stream()
            .filter(ScenarioState.ConcurrentRegistration::succeeded)
            .count()).isEqualTo(1);
    }

    @Then("one registration fails with a conflict")
    public void oneRegistrationFailsWithAConflict() {
        assertThat(state.getConcurrentRegistrations().stream()
            .filter(registration -> !registration.succeeded())
            .map(ScenarioState.ConcurrentRegistration::failure)
            .toList())
            .singleElement()
            .isInstanceOf(NoAvailableCompartmentException.class);
    }

    @Then("there are {int} stations")
    public void thereAreStations(int expected) {
        assertThat(data.stationCount()).isEqualTo(expected);
    }

    @Then("there are {int} compartments")
    public void thereAreCompartments(int expected) {
        assertThat(data.compartmentCount()).isEqualTo(expected);
    }

    private CompartmentSize size(String value) {
        return CompartmentSize.valueOf(value.toUpperCase());
    }
}
