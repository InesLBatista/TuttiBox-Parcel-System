package tuttibox.hw1.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data 
public class PickUpRequestDto {
    @NotBlank(message = "station id is mandatory")
    private String stationId;
    @NotBlank(message = "compartment id is mandatory")
    private String compartmentId;
    @NotBlank(message = "recipient id is mandatory")
    private String recipientId;
}