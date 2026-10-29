package tuttibox.hw1.dto;

import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.CompartmentStatus;

public record CompartmentResponseDto(
    Long id,
    String code,
    String stationCode,
    CompartmentSize size,
    CompartmentStatus status
) {
    public static CompartmentResponseDto from(Compartment compartment) {
        return new CompartmentResponseDto(
            compartment.getId(),
            compartment.getCode(),
            compartment.getStation().getCode(),
            compartment.getSize(),
            compartment.getStatus()
        );
    }
}
