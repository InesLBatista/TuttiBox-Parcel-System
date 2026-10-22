package tuttibox.hw1.dto;

import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.Station;

public record NearbyStationDto(
    String code,
    String name,
    double latitude,
    double longitude,
    double distanceKm,
    long availableSmall,
    long availableMedium,
    long availableLarge
) {
    public static NearbyStationDto from(Station station, double distanceKm, AvailabilityCounts availability) {
        return new NearbyStationDto(
            station.getCode(),
            station.getName(),
            station.getLatitude(),
            station.getLongitude(),
            distanceKm,
            availability.countOf(CompartmentSize.S),
            availability.countOf(CompartmentSize.M),
            availability.countOf(CompartmentSize.L)
        );
    }

    public static NearbyStationDto from(Station station, double distanceKm) {
        return from(station, distanceKm, AvailabilityCounts.empty());
    }
}
