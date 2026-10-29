package tuttibox.hw1.bdd;

import org.springframework.beans.factory.annotation.Autowired;

import io.cucumber.java.Before;
import tuttibox.hw1.fake.InMemoryGeocodingFake;
import tuttibox.hw1.fake.InMemoryHardwareFake;
import tuttibox.hw1.fake.InMemoryNotificationFake;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.ParcelRepository;
import tuttibox.hw1.repository.StationRepository;

public class Hooks {
    @Autowired
    private ParcelRepository parcelRepository;

    @Autowired
    private CompartmentRepository compartmentRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private InMemoryHardwareFake hardwareFake;

    @Autowired
    private InMemoryNotificationFake notificationFake;

    @Autowired
    private InMemoryGeocodingFake geocodingFake;

    @Autowired
    private ScenarioState state;

    @Before
    public void resetScenario() {
        parcelRepository.deleteAll();
        compartmentRepository.deleteAll();
        stationRepository.deleteAll();
        hardwareFake.setInjectFailure(false);
        hardwareFake.setInjectCloseFailure(false);
        hardwareFake.setInjectDoorNotClosed(false);
        notificationFake.clear();
        geocodingFake.clearFailure();
        state.reset();
    }
}
