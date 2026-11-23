package tn.esprit.simulateurbackend.dto;

import java.util.List;

public record FlashMarketCalendarResponse(
        String symbol,
        List<FlashMarketCalendarEvent> events
) {
    public static FlashMarketCalendarResponse empty(String symbol) {
        return new FlashMarketCalendarResponse(
                symbol == null ? "" : symbol,
                List.of()
        );
    }
}
