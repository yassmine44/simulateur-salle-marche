package tn.esprit.simulateurbackend.dto;

/**
 * One pre-game Market Calendar entry from the AI Context Engine.
 * JSON key {@code event} matches the Ollama calendar schema.
 */
public record FlashMarketCalendarEvent(
        String date,
        String event
) {
}
