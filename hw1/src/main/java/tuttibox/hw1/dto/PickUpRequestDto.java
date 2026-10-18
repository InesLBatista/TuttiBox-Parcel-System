package tuttibox.hw1.dto;

import lombok.Data;

@Data 
public class PickUpRequestDto {
    private String stationId;
    private String compartmentId;
    private String recipientId;
}