package tuttibox.hw1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data 
public class PickUpRequestDto {
    @NotBlank(message = "pickup code is mandatory")
    @Pattern(regexp = "(?i)^[A-HJ-NP-Z2-9]{8}$", message = "pickup code must have 8 valid characters")
    private String pickupCode;
}