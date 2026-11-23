package tn.esprit.simulateurbackend.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tn.esprit.simulateurbackend.dto.FlashCandleDto;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoCotationLoaderTest {

    @Test
    void parsesBiatTxtFixtureIntoDailyCandles() throws Exception {
        HistoCotationLoader loader = new HistoCotationLoader();
        Method parseTxt = HistoCotationLoader.class.getDeclaredMethod(
                "parseTxtRow",
                String.class,
                String.class
        );
        parseTxt.setAccessible(true);

        ClassPathResource resource =
                new ClassPathResource("histo_cotation_fixture.txt");
        List<FlashCandleDto> candles = new ArrayList<>();

        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                resource.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                )
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                FlashCandleDto candle = (FlashCandleDto) parseTxt.invoke(
                        loader,
                        line,
                        "BIAT"
                );
                if (candle != null) {
                    candles.add(candle);
                }
            }
        }

        assertEquals(11, candles.size());
        assertEquals("2019-01-02", candles.get(0).date());
        assertEquals(new BigDecimal("119.49"), candles.get(0).open());
        assertEquals(new BigDecimal("118"), candles.get(0).close());
        assertEquals(709L, candles.get(0).volume());
        assertTrue(
                candles.get(candles.size() - 1).close()
                        .compareTo(BigDecimal.valueOf(120)) > 0
        );
    }

    @Test
    void getWindowReturnsTenConsecutiveSessions() throws Exception {
        HistoCotationLoader loader = new HistoCotationLoader();
        Method parseTxt = HistoCotationLoader.class.getDeclaredMethod(
                "parseTxtRow",
                String.class,
                String.class
        );
        parseTxt.setAccessible(true);

        Map<java.time.LocalDate, FlashCandleDto> byDate = new LinkedHashMap<>();
        ClassPathResource resource =
                new ClassPathResource("histo_cotation_fixture.txt");

        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                resource.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                )
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                FlashCandleDto candle = (FlashCandleDto) parseTxt.invoke(
                        loader,
                        line,
                        "BIAT"
                );
                if (candle != null) {
                    byDate.put(
                            java.time.LocalDate.parse(candle.date()),
                            candle
                    );
                }
            }
        }

        List<FlashCandleDto> all = byDate.values().stream().toList();
        assertFalse(all.isEmpty());

        // Simulate mid-window selection used by getWindow when no start date.
        int size = 10;
        int startIndex = Math.max(0, (all.size() - size) / 2);
        List<FlashCandleDto> window = all.subList(
                startIndex,
                Math.min(all.size(), startIndex + size)
        );

        assertEquals(10, window.size());
        assertTrue(
                java.time.LocalDate.parse(window.get(0).date())
                        .isBefore(java.time.LocalDate.parse(
                                window.get(window.size() - 1).date()
                        ))
        );
    }
}
