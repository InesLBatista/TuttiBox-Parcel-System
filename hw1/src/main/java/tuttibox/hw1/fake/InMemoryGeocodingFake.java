package tuttibox.hw1.fake;

import java.util.HashMap;

import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import tuttibox.hw1.port.GeocodingException;
import tuttibox.hw1.port.GeocodingPort;
import tuttibox.hw1.model.*;;

@Component 
@Primary 
public class InMemoryGeocodingFake implements GeocodingPort {
    private final Map<String, Coordinates> addressDatabase = new HashMap<>();
    private GeocodingException.Reason simulatedFailure;

    public InMemoryGeocodingFake() {
        //dados para testes/demonstração
        addressDatabase.put("Anadia", new Coordinates(40.6305, -8.6571));
        addressDatabase.put("Aradas", new Coordinates(40.6418, -8.6444));
    }

    public void setSimulateUnavailable(boolean simulateUnavailable) {
        simulatedFailure = simulateUnavailable ? GeocodingException.Reason.UNAVAILABLE : null;
    }

    public void setFailure(GeocodingException.Reason reason) {
        this.simulatedFailure = reason;
    }

    public void clearFailure() {
        this.simulatedFailure = null;
    }

    public void addAddress(String address, Coordinates coordinates) {
        addressDatabase.put(address, coordinates);
    }

    @Override 
    public Optional<Coordinates> getCoordinates(String address) {
        if (simulatedFailure != null) {
            throw new GeocodingException(simulatedFailure, messageFor(simulatedFailure));
        }

        return Optional.ofNullable(addressDatabase.get(address));
    }

    private String messageFor(GeocodingException.Reason reason) {
        return switch (reason) {
            case UNAVAILABLE -> "geocoding service is unavailable";
            case TIMEOUT -> "geocoding service timed out";
            case RATE_LIMITED -> "geocoding service usage limit exceeded";
            case INVALID_RESPONSE -> "geocoding service returned an invalid response";
            case PROVIDER_ERROR -> "geocoding service returned an error";
        };
    }
}
