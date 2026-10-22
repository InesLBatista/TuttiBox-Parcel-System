package tuttibox.hw1.dto;

import java.util.EnumMap;
import java.util.Map;

import tuttibox.hw1.model.CompartmentSize;

public record AvailabilityCounts(Map<CompartmentSize, Long> countsBySize) {
    public static AvailabilityCounts empty() {
        return new AvailabilityCounts(new EnumMap<>(CompartmentSize.class));
    }

    public static AvailabilityCounts of(Map<CompartmentSize, Long> countsBySize) {
        return new AvailabilityCounts(new EnumMap<>(countsBySize));
    }

    public long countOf(CompartmentSize size) {
        return countsBySize.getOrDefault(size, 0L);
    }
}
