package tuttibox.hw1.dto;

import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelState;

public record ParcelResponseDto(
    Long id,
    String trackingCode,
    String recipientContact,
    String compartmentSize,
    String partnerId,
    ParcelState currentState,
    String assignedCompartmentCode,
    String assignedStationCode
) {
    //factory diretamente da entidade
    public static ParcelResponseDto from(Parcel p) {
        return new ParcelResponseDto(
            p.getId(),
            p.getTrackingCode(),
            p.getRecepientContact(),
            p.getCompartmentSize(),
            p.getPartnerId(),
            p.getCurrentState(),
            p.getAssignedCompartment() != null ? p.getAssignedCompartment().getCode() : null,
            p.getStation() != null ? p.getStation().getCode() : null
        );
    }
}
