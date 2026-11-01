package tn.esprit.simulateurbackend.service;

import jakarta.annotation.PostConstruct;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.GamificationCandleResponse;
import tn.esprit.simulateurbackend.entity.GamificationEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/*
 * Charge une seule fois les CSV OHLCV hebdomadaires
 * depuis src/main/resources.
 */
@Service
public class GamificationHistoricalDataService {

    private final Map<GamificationEvent, List<GamificationCandleResponse>>
            cache = new EnumMap<>(GamificationEvent.class);


    @PostConstruct
    void loadAll() {

        for (GamificationEvent event :
                GamificationEvent.values()) {

            cache.put(
                    event,
                    loadCsv(event)
            );
        }
    }


    public List<GamificationCandleResponse> getCandles(
            GamificationEvent event
    ) {

        List<GamificationCandleResponse> candles =
                cache.get(event);

        if (candles == null || candles.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Données historiques indisponibles pour "
                            + event.getDisplayName()
            );
        }

        return candles;
    }


    public GamificationCandleResponse getCandleAt(
            GamificationEvent event,
            int index
    ) {

        List<GamificationCandleResponse> candles =
                getCandles(event);

        if (index < 0 || index >= candles.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Index de bougie hors limites."
            );
        }

        return candles.get(index);
    }


    private List<GamificationCandleResponse> loadCsv(
            GamificationEvent event
    ) {

        ClassPathResource resource =
                new ClassPathResource(
                        event.getCsvFileName()
                );

        if (!resource.exists()) {
            throw new IllegalStateException(
                    "CSV introuvable : "
                            + event.getCsvFileName()
            );
        }

        List<GamificationCandleResponse> candles =
                new ArrayList<>();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        resource.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            String line;
            boolean first = true;

            while ((line = reader.readLine()) != null) {

                if (first) {
                    first = false;
                    continue;
                }

                if (line.isBlank()) {
                    continue;
                }

                candles.add(parseLine(line));
            }

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Impossible de lire "
                            + event.getCsvFileName(),
                    exception
            );
        }

        if (candles.isEmpty()) {
            throw new IllegalStateException(
                    "CSV vide : " + event.getCsvFileName()
            );
        }

        return List.copyOf(candles);
    }


    private GamificationCandleResponse parseLine(
            String line
    ) {

        String[] parts = line.split(",", -1);

        if (parts.length < 7) {
            throw new IllegalStateException(
                    "Ligne CSV invalide : " + line
            );
        }

        return new GamificationCandleResponse(
                parts[0].trim(),
                new BigDecimal(parts[1].trim()),
                new BigDecimal(parts[2].trim()),
                new BigDecimal(parts[3].trim()),
                new BigDecimal(parts[4].trim()),
                Long.parseLong(parts[6].trim())
        );
    }
}
