package tn.esprit.simulateurbackend.dto;

/**
 * Admin start payload. {@code symbol} is the verified Tunisian ticker
 * selected in the Admin UI (e.g. BIAT, SFBT) and is injected into the
 * AI Context Engine system prompt.
 */
public record FlashStartRequest(
        String symbol
) {
}
