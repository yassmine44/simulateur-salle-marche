package tn.esprit.simulateurbackend.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import tn.esprit.simulateurbackend.dto.FlashCandleDto;
import tn.esprit.simulateurbackend.dto.FlashMarketCalendarEvent;
import tn.esprit.simulateurbackend.dto.FlashMarketCalendarResponse;
import tn.esprit.simulateurbackend.dto.FlashVoiceInsightResponse;

/**
 * Two isolated Ollama flows:
 * 1) {@link #generateMarketCalendar(String)} — once on session start
 * 2) {@link #analyze(String, List, List)} — every 15s candle tick (voice only)
 */
@Service
public class FlashVoiceAnalystService {

    private static final Logger log =
            LoggerFactory.getLogger(FlashVoiceAnalystService.class);

    private static final String NGROK_SKIP_HEADER =
            "ngrok-skip-browser-warning";

    private final boolean enabled;
    private final String model;
    private final RestClient restClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public FlashVoiceAnalystService(
            RestClient.Builder builder,
            @Value("${app.flash.ai.enabled:false}") boolean enabled,
            @Value("${app.flash.ai.ollama.model:llama3.2}") String model,
            @Value("${app.flash.ai.ollama.base-url:https://modular-spectacle-stopping.ngrok-free.dev}")
            String baseUrl,
            @Value("${app.flash.ai.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.flash.ai.read-timeout:45s}") Duration readTimeout
    ) {
        this.enabled = enabled;
        this.model = model == null || model.isBlank() ? "llama3.2" : model.trim();
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.restClient = builder
                .baseUrl(normalized)
                .defaultHeader(NGROK_SKIP_HEADER, "69420")
                .requestFactory(requestFactory)
                .build();
        log.info(
                "Flash AI clients ready: baseUrl={}, model={}",
                normalized,
                this.model
        );
    }


    /**
     * Flow 1 — Pre-game Market Calendar (ONE call per selected company).
     * Does not touch live voice state.
     */
    public FlashMarketCalendarResponse generateMarketCalendar(String symbol) {
        String selectedCompany = normalizeCompany(symbol);
        if (!enabled || model.isBlank()) {
            log.warn("Market calendar skipped (AI disabled or model blank)");
            return FlashMarketCalendarResponse.empty(selectedCompany);
        }

        try {
            String response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(NGROK_SKIP_HEADER, "69420")
                    .body(calendarRequestBody(selectedCompany))
                    .retrieve()
                    .body(String.class);

            List<FlashMarketCalendarEvent> events =
                    parseCalendarEvents(response);
            log.info(
                    "Market calendar generated for {}: {} events",
                    selectedCompany,
                    events.size()
            );
            return new FlashMarketCalendarResponse(selectedCompany, events);
        } catch (RestClientException exception) {
            log.error("Market calendar Ollama unavailable: {}", exception.getMessage());
            return FlashMarketCalendarResponse.empty(selectedCompany);
        } catch (RuntimeException exception) {
            log.error("Calendar Parse Error: {}", exception.getMessage());
            return FlashMarketCalendarResponse.empty(selectedCompany);
        }
    }


    public FlashVoiceInsightResponse analyze(
            String symbol,
            List<FlashCandleDto> candles
    ) {
        return analyze(symbol, candles, List.of());
    }


    /**
     * Flow 2 — Live AI Voice Commentator (every candle tick).
     * Returns voice_script only; never mutates calendar events.
     */
    public FlashVoiceInsightResponse analyze(
            String symbol,
            List<FlashCandleDto> candles,
            List<String> calendarContext
    ) {
        if (candles == null || candles.isEmpty()) {
            return FlashVoiceInsightResponse.silent();
        }

        String simulationDate = candles.get(candles.size() - 1).date();
        if (simulationDate == null) {
            simulationDate = "";
        }

        if (!enabled || model.isBlank()) {
            log.warn(
                    "Flash voice disabled or model blank (enabled={}, model blank={}); staying silent",
                    enabled,
                    model.isBlank()
            );
            return FlashVoiceInsightResponse.silent();
        }

        try {
            String response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(NGROK_SKIP_HEADER, "69420")
                    .body(voiceRequestBody(
                            symbol,
                            candles,
                            simulationDate,
                            calendarContext
                    ))
                    .retrieve()
                    .body(String.class);

            FlashVoiceInsightResponse parsed =
                    parseVoiceInsight(response, simulationDate);
            if (parsed == null) {
                log.warn("Flash voice Ollama returned empty/invalid JSON; staying silent");
                return FlashVoiceInsightResponse.silent();
            }
            return parsed;
        } catch (RestClientException exception) {
            log.error("Flash voice Ollama unavailable: {}", exception.getMessage());
            return FlashVoiceInsightResponse.silent();
        } catch (RuntimeException exception) {
            log.error("Flash voice parse failed: {}", exception.getMessage());
            return FlashVoiceInsightResponse.silent();
        }
    }


    private Map<String, Object> calendarRequestBody(String selectedCompany) {
        String systemPrompt = """
                Generate a historical timeline of 6 to 8 real-world macroeconomic \
                and sector-specific events in Tunisia during 2018 that would logically \
                impact the stock of %s.
                CRITICAL INSTRUCTION: You must return ONLY a raw, stringified JSON array. \
                Do not use markdown formatting, do not use backticks, and do NOT say \
                "Here is the JSON". Start your response immediately with [ and end it with ].
                Format strictly like this:
                [ {"date": "2018-02-15", "event": "Central Bank..."} ]
                """.formatted(selectedCompany);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", "llama3.2");
        payload.put("stream", false);
        // No "format":"json" here — we require a bare array starting with '['.
        payload.put("messages", new Object[]{
                Map.of("role", "system", "content", systemPrompt),
                Map.of(
                        "role", "user",
                        "content", "Selected company: " + selectedCompany
                                + ". Return ONLY the raw JSON array now. Start with [."
                )
        });
        return payload;
    }


    private Map<String, Object> voiceRequestBody(
            String symbol,
            List<FlashCandleDto> candles,
            String simulationDate,
            List<String> calendarContext
    ) {
        String selectedCompany = normalizeCompany(symbol);
        FlashCandleDto latest = candles.get(candles.size() - 1);
        FlashCandleDto previous = candles.size() > 1
                ? candles.get(candles.size() - 2)
                : latest;

        String latestSummary = "Latest closed candle " + latest.date()
                + " O:" + latest.open()
                + " H:" + latest.high()
                + " L:" + latest.low()
                + " C:" + latest.close()
                + " V:" + latest.volume()
                + " | Previous candle " + previous.date()
                + " O:" + previous.open()
                + " H:" + previous.high()
                + " L:" + previous.low()
                + " C:" + previous.close()
                + " V:" + previous.volume();

        String calendarHint = (calendarContext == null || calendarContext.isEmpty())
                ? "No matching Market Calendar event for this exact period."
                : "Relevant Market Calendar: " + String.join(" | ", calendarContext);

        String systemPrompt = """
                Role: You are the live-action AI Commentator for a multiplayer trading \
                simulator using real historical data from the Tunis Stock Exchange, \
                starting in 2018.
                Current Session Company: %s

                Your Directive: Live Candle Analysis
                The chart is updating slowly. I will feed you the data for the most \
                recently closed candle. You must generate a rapid, analytical market \
                update script to be read by our AI Voice while the players wait for \
                the next candle.

                CRITICAL CONSTRAINTS:
                - Length limit: You are strictly limited to a maximum of 30 words.
                - Content: Analyze what just happened in this specific candle \
                (did it gap up, is volume surging, is it hitting resistance?). \
                If there is a relevant event in the Market Calendar for this exact \
                time period, mention it briefly.
                - Tone: Sound like an intense, fast-talking live sports commentator, \
                but for the stock market. Keep the momentum high. No filler words.

                Current simulation date: %s

                Return ONLY this JSON object (no markdown, no preamble):
                {
                  "voice_script": "Maximum 30 words. Punchy live candle commentary."
                }
                """.formatted(selectedCompany, simulationDate);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", "llama3.2");
        payload.put("stream", false);
        payload.put("format", "json");
        payload.put("messages", new Object[]{
                Map.of("role", "system", "content", systemPrompt),
                Map.of(
                        "role", "user",
                        "content", "Selected company: " + selectedCompany
                                + ". " + latestSummary
                                + ". " + calendarHint
                                + " Generate voice_script now (max 30 words)."
                )
        });
        return payload;
    }


    /**
     * Robust calendar parse: slice from first '[' to last ']', then JSON.parse.
     */
    private List<FlashMarketCalendarEvent> parseCalendarEvents(String rawOllamaJson) {
        String content = extractMessageContent(rawOllamaJson);
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String text = content
                .replaceAll("(?i)```json", "")
                .replace("```", "")
                .trim();

        int jsonStart = text.indexOf('[');
        int jsonEnd = text.lastIndexOf(']');
        if (jsonStart < 0 || jsonEnd < 0 || jsonEnd <= jsonStart) {
            log.error("Calendar Parse Error: no JSON array brackets found");
            return List.of();
        }

        String jsonString = text.substring(jsonStart, jsonEnd + 1);
        JsonNode arrayNode = jsonMapper.readTree(jsonString);
        if (!arrayNode.isArray()) {
            return List.of();
        }

        List<FlashMarketCalendarEvent> events = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            String date = textOrEmpty(node, "date");
            String event = textOrEmpty(node, "event");
            if (event.isBlank()) {
                event = textOrEmpty(node, "text");
            }
            if (event.isBlank()) {
                continue;
            }
            events.add(new FlashMarketCalendarEvent(
                    date.isBlank() ? "2018-01-01" : date,
                    event
            ));
        }
        return List.copyOf(events);
    }


    private FlashVoiceInsightResponse parseVoiceInsight(
            String rawOllamaJson,
            String simulationDate
    ) {
        String content = extractMessageContent(rawOllamaJson);
        if (content == null || content.isBlank()) {
            return null;
        }

        String cleanJsonString = content
                .replaceAll("(?i)```json", "")
                .replace("```", "")
                .trim();

        // Prefer object slice if the model wraps prose around JSON.
        int objStart = cleanJsonString.indexOf('{');
        int objEnd = cleanJsonString.lastIndexOf('}');
        if (objStart >= 0 && objEnd > objStart) {
            cleanJsonString = cleanJsonString.substring(objStart, objEnd + 1);
        }

        JsonNode payload = jsonMapper.readTree(cleanJsonString);
        String voiceScript = textOrEmpty(payload, "voice_script");
        if (voiceScript.isBlank()) {
            // Some models return a bare string field "response".
            voiceScript = textOrEmpty(payload, "response");
        }
        if (voiceScript.isBlank()) {
            return null;
        }

        // Never write calendar_event from the live voice path.
        return new FlashVoiceInsightResponse(
                voiceScript,
                "",
                simulationDate,
                true
        );
    }


    private String extractMessageContent(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        String cleanedEnvelope = rawJson
                .replaceAll("(?i)```json", "")
                .replace("```", "")
                .trim();
        try {
            JsonNode root = jsonMapper.readTree(cleanedEnvelope);
            JsonNode message = root.path("message").path("content");
            if (message.isString() && !message.stringValue().isBlank()) {
                return message.stringValue();
            }
            JsonNode content = root.path("response");
            if (content.isString() && !content.stringValue().isBlank()) {
                return content.stringValue();
            }
            if (root.isArray() || root.isObject()) {
                return cleanedEnvelope;
            }
        } catch (RuntimeException ignored) {
            // Not JSON — treat whole body as model text (calendar slice still works).
            return cleanedEnvelope;
        }
        return cleanedEnvelope;
    }


    private static String normalizeCompany(String symbol) {
        return symbol == null || symbol.isBlank() ? "BIAT" : symbol.trim();
    }


    private static String textOrEmpty(JsonNode node, String field) {
        if (node == null || !node.has(field)) {
            return "";
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return "";
        }
        String text = value.isString() ? value.stringValue() : value.toString();
        return text == null ? "" : text.trim();
    }
}
