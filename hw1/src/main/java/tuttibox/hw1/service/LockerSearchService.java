package tuttibox.hw1.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import tuttibox.hw1.model.Coordinates;
import tuttibox.hw1.port.GeocodingPort;

@Service 
public class LockerSearchService {
    private final GeocodingPort geocodingPort;

    public LockerSearchService(GeocodingPort geocodingPort) {
        this.geocodingPort = geocodingPort;
    }

    public Optional<Coordinates> findNearestStations(String address) {
        return geocodingPort.getCoordinates(address);
    }
}
