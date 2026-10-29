package tuttibox.hw1.bdd;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityNotFoundException;
import tuttibox.hw1.config.DeadlineProperties;
import tuttibox.hw1.config.ParcelLifecycleScheduler;
import tuttibox.hw1.config.SeedData;
import tuttibox.hw1.dto.ParcelRequestDto;
import tuttibox.hw1.dto.ParcelResponseDto;
import tuttibox.hw1.fake.InMemoryGeocodingFake;
import tuttibox.hw1.fake.InMemoryHardwareFake;
import tuttibox.hw1.fake.InMemoryNotificationFake;
import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.CompartmentStatus;
import tuttibox.hw1.model.Coordinates;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelState;
import tuttibox.hw1.model.Station;
import tuttibox.hw1.port.GeocodingException;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.ParcelRepository;
import tuttibox.hw1.repository.StationRepository;
import tuttibox.hw1.service.ParcelService;
import tuttibox.hw1.service.PickupCode;

@Component
public class TestDataSupport {
    @Autowired
    private StationRepository stationRepository;
    @Autowired
    private CompartmentRepository compartmentRepository;
    @Autowired
    private ParcelRepository parcelRepository;
    @Autowired
    private ParcelService parcelService;
    @Autowired
    private ParcelLifecycleScheduler scheduler;
    @Autowired
    private SeedData seedData;
    @Autowired
    private InMemoryHardwareFake hardwareFake;
    @Autowired
    private InMemoryNotificationFake notificationFake;
    @Autowired
    private InMemoryGeocodingFake geocodingFake;
    @Autowired
    private DeadlineProperties deadlineProperties;

    public Station station(String code, String name, double latitude, double longitude) {
        return stationRepository.findByCode(code)
            .orElseGet(() -> stationRepository.save(new Station(code, name, latitude, longitude)));
    }

    public Station requireStation(String code) {
        return stationRepository.findByCode(code)
            .orElseThrow(() -> new EntityNotFoundException("estação não encontrada: " + code));
    }

    public Compartment compartment(
            String code, String stationCode, CompartmentSize size, CompartmentStatus status) {
        Station station = requireStation(stationCode);
        Compartment compartment = compartmentRepository.findByStationCodeAndCode(stationCode, code)
            .orElseGet(() -> compartmentRepository.save(new Compartment(code, station, size)));
        applyStatus(compartment, status);
        return compartmentRepository.save(compartment);
    }

    public Parcel parcel(
            String trackingCode,
            String recipientContact,
            CompartmentSize size,
            String stationCode,
            String compartmentCode,
            ParcelState state) {
        Station station = requireStation(stationCode);
        Compartment compartment = compartment(
            compartmentCode, stationCode, size, statusFor(state));

        Parcel parcel = new Parcel(trackingCode, recipientContact, size.name());
        parcel.setStation(station);
        parcel.setAssignedCompartment(compartment);
        parcel.setPartnerId("PARTNER_BDD");
        LocalDateTime now = LocalDateTime.now();
        parcel.setRegisteredAt(now);
        parcel.setDropOffDeadline(now.plus(deadlineProperties.getDropOff()));

        switch (state) {
            case CREATED -> {
            }
            case READY_FOR_PICKUP -> parcel.markAsReadyForPickup(
                "COURIER_BDD", now.plus(deadlineProperties.getPickup()));
            case EXPIRED -> {
                parcel.markAsReadyForPickup("COURIER_BDD", now.plus(deadlineProperties.getPickup()));
                parcel.expire("SYSTEM");
            }
            case PICKED_UP -> {
                parcel.markAsReadyForPickup("COURIER_BDD", now.plus(deadlineProperties.getPickup()));
                parcel.pickUp("RECIPIENT");
            }
            case CANCELLED -> parcel.cancel("PARTNER_BDD");
            case RETURNED -> {
                parcel.markAsReadyForPickup("COURIER_BDD", now.plus(deadlineProperties.getPickup()));
                parcel.expire("SYSTEM");
                parcel.returnParcel("OPERATOR_BDD");
            }
        }

        return parcelRepository.save(parcel);
    }

    public Parcel readyParcel(
            String trackingCode,
            String recipientContact,
            CompartmentSize size,
            String stationCode,
            String compartmentCode,
            String pickupCode) {
        Parcel parcel = parcel(
            trackingCode, recipientContact, size, stationCode, compartmentCode, ParcelState.READY_FOR_PICKUP);
        parcel.issuePickupCode(PickupCode.hash(pickupCode));
        return parcelRepository.save(parcel);
    }

    public void setDropOffDeadline(String trackingCode, String relative) {
        Parcel parcel = requireParcel(trackingCode);
        parcel.setDropOffDeadline(relativeTime(relative));
        parcelRepository.save(parcel);
    }

    public void setPickupDeadline(String trackingCode, String relative) {
        Parcel parcel = requireParcel(trackingCode);
        parcel.setPickupDeadline(relativeTime(relative));
        parcelRepository.save(parcel);
    }

    public void expireParcel(String trackingCode) {
        Parcel parcel = requireParcel(trackingCode);
        parcel.expire("SYSTEM");
        parcelRepository.save(parcel);
    }

    public void applyDeadlines() {
        parcelService.applyDeadlines();
    }

    public void runScheduler() {
        scheduler.applyDeadlines();
    }

    public Parcel requireParcel(String trackingCode) {
        return parcelRepository.findByTrackingCode(trackingCode)
            .orElseThrow(() -> new EntityNotFoundException("encomenda não foi encontrada: " + trackingCode));
    }

    public List<ScenarioState.ConcurrentRegistration> registerConcurrently(
            CompartmentSize size, String stationCode) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ScenarioState.ConcurrentRegistration> first = executor.submit(
                () -> registerConcurrently("PARTNER_A", size, stationCode, ready, start));
            Future<ScenarioState.ConcurrentRegistration> second = executor.submit(
                () -> registerConcurrently("PARTNER_B", size, stationCode, ready, start));

            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent registrations did not become ready");
            }
            start.countDown();

            return List.of(
                first.get(15, TimeUnit.SECONDS),
                second.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    public void failGeocoding(String failure) {
        geocodingFake.setFailure(reasonFor(failure));
    }

    public void resolveAddress(String address, double latitude, double longitude) {
        geocodingFake.addAddress(address, new Coordinates(latitude, longitude));
    }

    public void hardwareFailsToOpen() {
        hardwareFake.setInjectFailure(true);
    }

    public void hardwareFailsToClose() {
        hardwareFake.setInjectCloseFailure(true);
    }

    public void hardwareReportsDoorOpen() {
        hardwareFake.setInjectDoorNotClosed(true);
    }

    public void clearHardwareFailures() {
        hardwareFake.setInjectFailure(false);
        hardwareFake.setInjectCloseFailure(false);
        hardwareFake.setInjectDoorNotClosed(false);
    }

    public boolean isDoorClosed(String stationCode, String compartmentCode) {
        return hardwareFake.isDoorClosed(stationCode, compartmentCode);
    }

    public List<InMemoryNotificationFake.Notification> notifications() {
        return notificationFake.getNotifications();
    }

    public void seedTwice() {
        seedData.seed();
        seedData.seed();
    }

    public long parcelCount() {
        return parcelRepository.count();
    }

    public long stationCount() {
        return stationRepository.count();
    }

    public long compartmentCount() {
        return compartmentRepository.count();
    }

    private ScenarioState.ConcurrentRegistration registerConcurrently(
            String partnerId,
            CompartmentSize size,
            String stationCode,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);

        try {
            ParcelRequestDto request = new ParcelRequestDto();
            request.setStationId(stationCode);
            request.setCompartmentSize(size.name());
            request.setRecipientContact(partnerId.toLowerCase() + "@example.com");
            request.setPartnerId(partnerId);

            ParcelResponseDto response = parcelService.registerParcel(request);
            return new ScenarioState.ConcurrentRegistration(
                partnerId, response.trackingCode(), null);
        } catch (Throwable failure) {
            return new ScenarioState.ConcurrentRegistration(partnerId, null, failure);
        }
    }

    private CompartmentStatus statusFor(ParcelState state) {
        return switch (state) {
            case CREATED -> CompartmentStatus.RESERVED;
            case READY_FOR_PICKUP, EXPIRED -> CompartmentStatus.OCCUPIED;
            case CANCELLED, PICKED_UP, RETURNED -> CompartmentStatus.FREE;
        };
    }

    private void applyStatus(Compartment compartment, CompartmentStatus target) {
        if (compartment.getStatus() == target) {
            return;
        }

        switch (target) {
            case FREE -> {
                if (compartment.getStatus() == CompartmentStatus.OUT_OF_SERVICE) {
                    compartment.putInService();
                } else {
                    compartment.release();
                }
            }
            case RESERVED -> {
                if (compartment.getStatus() != CompartmentStatus.FREE) {
                    throw new IllegalStateException(
                        "cannot reserve compartment in status " + compartment.getStatus());
                }
                compartment.reserve();
            }
            case OCCUPIED -> {
                if (compartment.getStatus() == CompartmentStatus.FREE) {
                    compartment.reserve();
                }
                if (compartment.getStatus() != CompartmentStatus.RESERVED) {
                    throw new IllegalStateException(
                        "cannot occupy compartment in status " + compartment.getStatus());
                }
                compartment.occupy();
            }
            case OUT_OF_SERVICE -> {
                if (compartment.getStatus() != CompartmentStatus.FREE) {
                    throw new IllegalStateException(
                        "cannot put compartment out of service in status " + compartment.getStatus());
                }
                compartment.putOutOfService();
            }
        }
    }

    private LocalDateTime relativeTime(String relative) {
        return switch (relative.toLowerCase()) {
            case "in the past" -> LocalDateTime.now().minusMinutes(1);
            case "in the future" -> LocalDateTime.now().plusHours(1);
            default -> throw new IllegalArgumentException("unknown deadline: " + relative);
        };
    }

    private GeocodingException.Reason reasonFor(String failure) {
        return switch (failure.toLowerCase()) {
            case "unavailable" -> GeocodingException.Reason.UNAVAILABLE;
            case "timeout" -> GeocodingException.Reason.TIMEOUT;
            case "rate limited" -> GeocodingException.Reason.RATE_LIMITED;
            case "invalid response" -> GeocodingException.Reason.INVALID_RESPONSE;
            case "provider error" -> GeocodingException.Reason.PROVIDER_ERROR;
            default -> throw new IllegalArgumentException("unknown geocoding failure: " + failure);
        };
    }
}
