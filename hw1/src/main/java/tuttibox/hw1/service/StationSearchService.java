package tuttibox.hw1.service;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tuttibox.hw1.dto.AvailabilityCounts;
import tuttibox.hw1.dto.NearbyStationDto;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.CompartmentStatus;
import tuttibox.hw1.model.Coordinates;
import tuttibox.hw1.model.Station;
import tuttibox.hw1.port.GeocodingPort;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.StationRepository;

@Service 
public class StationSearchService {
    static final double EARTH_RADIUS_KM = 6371.0088;

    private final GeocodingPort geocodingPort;
    private final StationRepository stationRepository;
    private final CompartmentRepository compartmentRepository;

    public StationSearchService(
        GeocodingPort geocodingPort,
        StationRepository stationRepository,
        CompartmentRepository compartmentRepository
    ) {
        this.geocodingPort = geocodingPort;
        this.stationRepository = stationRepository;
        this.compartmentRepository = compartmentRepository;
    }

    @Transactional(readOnly = true)
    public List<NearbyStationDto> findNearestStations(String address) {
        Optional<Coordinates> origin = geocodingPort.getCoordinates(address);
        if (origin.isEmpty()) {
            return List.of();
        }

        Coordinates from = origin.get();
        List<Station> stations = stationRepository.findAll();

        Map<String, AvailabilityCounts> availability = availabilityByStation(stations);

        return stations.stream()
            .map(station -> NearbyStationDto.from(
                station,
                distanceKm(from, new Coordinates(station.getLatitude(), station.getLongitude())),
                availability.getOrDefault(station.getCode(), AvailabilityCounts.empty())))
            .sorted(Comparator.comparingDouble((NearbyStationDto dto) ->
                    Objects.requireNonNull(dto).distanceKm())
                .thenComparing(dto -> Objects.requireNonNull(dto).code()))
            .toList();
    }

    private Map<String, AvailabilityCounts> availabilityByStation(List<Station> stations) {
        if (stations.isEmpty()) {
            return Map.of();
        }

        List<String> stationCodes = stations.stream()
            .map(station -> Objects.requireNonNull(station).getCode())
            .toList();

        Map<String, Map<CompartmentSize, Long>> countsByStation = new java.util.HashMap<>();
        compartmentRepository.countByStationCodesAndStatusGroupedBySize(stationCodes, CompartmentStatus.FREE)
            .forEach(projection -> countsByStation
                .computeIfAbsent(projection.getStationCode(), code -> new EnumMap<>(CompartmentSize.class))
                .put(projection.getSize(), projection.getTotal()));

        Map<String, AvailabilityCounts> result = new java.util.HashMap<>();
        countsByStation.forEach((code, counts) -> result.put(code, AvailabilityCounts.of(counts)));
        return result;
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
