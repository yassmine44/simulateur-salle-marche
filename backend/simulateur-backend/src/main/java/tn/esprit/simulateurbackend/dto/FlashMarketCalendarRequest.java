package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FlashMarketCalendarRequest(
        @NotBlank
        @Size(max = 80)
        String symbol
) {
}
