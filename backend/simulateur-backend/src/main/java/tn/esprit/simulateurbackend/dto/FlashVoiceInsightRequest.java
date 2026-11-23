package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FlashVoiceInsightRequest(
        @NotEmpty
        @Size(max = 30)
        List<FlashCandleDto> candles,
        String symbol,
        List<String> calendarContext
) {
}
