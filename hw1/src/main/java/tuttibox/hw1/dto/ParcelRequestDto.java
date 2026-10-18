package tuttibox.hw1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ParcelRequestDto {
    @NotBlank(message = "stationId é obrigatório")
    private String stationId;

    @NotBlank(message = "compartmentSize é obrigatório")
    @Pattern(regexp = "^[SMLsml]$", message = "compartmentSize deve ser S, M ou L")
    private String compartmentSize;

    @NotBlank(message = "recipientContact é obrigatório")
    private String recipientContact;
    
    @NotBlank(message = "partnerId é obrigatório")
    private String partnerId;
}
