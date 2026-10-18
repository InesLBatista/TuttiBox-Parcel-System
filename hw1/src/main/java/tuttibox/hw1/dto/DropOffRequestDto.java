package tuttibox.hw1.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data 
public class DropOffRequestDto {
    @NotBlank(message = "station id is mandatory")
    private String stationId;
    @NotBlank(message = "compartment id is mandatory")
    private String compartmentId;
    @NotBlank(message = "courier id is mandatory")
    private String courierId;
}
