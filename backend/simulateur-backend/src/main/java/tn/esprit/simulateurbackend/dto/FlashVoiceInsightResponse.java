package tn.esprit.simulateurbackend.dto;

/**
 * Dual AI output: short TTS script + historical calendar blurb for the UI.
 */
public record FlashVoiceInsightResponse(
        String voiceScript,
        String calendarEvent,
        String simulationDate,
        boolean spokenReady
) {
    public static FlashVoiceInsightResponse silent() {
        return new FlashVoiceInsightResponse("", "", "", false);
    }
}
