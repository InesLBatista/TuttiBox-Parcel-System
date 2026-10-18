package tuttibox.hw1.dto;

import lombok.Data;

@Data 
public class DropOffRequestDto {
    private String stationId;
    private String compartmentId;
    private String courierId;
}
