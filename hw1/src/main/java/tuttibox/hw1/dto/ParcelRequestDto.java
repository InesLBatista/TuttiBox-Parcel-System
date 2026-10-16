package tuttibox.hw1.dto;

import lombok.Data;

@Data 
public class ParcelRequestDto {
    private String stationId;
    private String compartmentSize;
    private String recipientContact;
    private String partnerId;
}
