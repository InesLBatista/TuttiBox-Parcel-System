package tuttibox.hw1.dto;

import tuttibox.hw1.model.Station;

public record NearbyStationDto(
    String code,
    String name,
    double latitude,
    double longitude,
    double distanceKm
) {
    public static NearbyStationDto from(Station station, double distanceKm) {
        return new NearbyStationDto(
            station.getCode(),
            station.getName(),
            station.getLatitude(),
            station.getLongitude(),
            distanceKm
        );
    }
}
