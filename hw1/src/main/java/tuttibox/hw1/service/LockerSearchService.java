package tuttibox.hw1.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import tuttibox.hw1.dto.NearbyStationDto;
import tuttibox.hw1.model.Coordinates;
import tuttibox.hw1.port.GeocodingPort;
import tuttibox.hw1.repository.StationRepository;

@Service 
public class LockerSearchService {
    static final double EARTH_RADIUS_KM = 6371.0088;

    private final GeocodingPort geocodingPort;
    private final StationRepository stationRepository;

    public LockerSearchService(GeocodingPort geocodingPort, StationRepository stationRepository) {
        this.geocodingPort = geocodingPort;
        this.stationRepository = stationRepository;
    }

    public List<NearbyStationDto> findNearestStations(String address) {
        Optional<Coordinates> origin = geocodingPort.getCoordinates(address);
        if (origin.isEmpty()) {
            return List.of();
        }

        Coordinates from = origin.get();
        return stationRepository.findAll().stream()
            .map(station -> NearbyStationDto.from(
                station,
                distanceKm(from, new Coordinates(station.getLatitude(), station.getLongitude()))))
            .sorted(Comparator.comparingDouble(NearbyStationDto::distanceKm)
                .thenComparing(NearbyStationDto::code))
            .toList();
    }

    static double distanceKm(Coordinates from, Coordinates to) {
        double lat1 = Math.toRadians(from.getLatitude());
        double lat2 = Math.toRadians(to.getLatitude());
        double deltaLat = Math.toRadians(to.getLatitude() - from.getLatitude());
        double deltaLon = Math.toRadians(to.getLongitude() - from.getLongitude());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
            + Math.cos(lat1) * Math.cos(lat2)
            * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
