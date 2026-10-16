package tuttibox.hw1.port;

import java.util.Optional;

import tuttibox.hw1.model.Coordinates;

public interface GeocodingPort {
    Optional<Coordinates> getCoordinates(String address);
}
