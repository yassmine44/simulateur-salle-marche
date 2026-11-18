package tn.esprit.simulateurbackend.service;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.FlashCandleDto;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses BVMT histo_cotation TXT/CSV resources into per-symbol daily OHLCV series.
 */
@Service
public class HistoCotationLoader {

    private static final Logger log =
            LoggerFactory.getLogger(HistoCotationLoader.class);

    public static final String DEFAULT_SYMBOL = "BIAT";
    public static final int DEFAULT_WINDOW_SIZE = 10;
    public static final int PRIMARY_DATASET_FROM_YEAR = 2018;
    public static final int PRIMARY_DATASET_TO_YEAR = 2026;
    public static final String PRIMARY_DATASET_LABEL =
            "2018 - 2026 Historical Market";

    private static final DateTimeFormatter TXT_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final DateTimeFormatter CSV_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * TXT rows: date, group, code, lib (may contain spaces), then numeric fields
     * with French decimal commas.
     */
    private static final Pattern TXT_ROW = Pattern.compile(
            "^(\\d{2}/\\d{2}/\\d{2})\\s+(\\d+)\\s+(\\S+)\\s+(.+?)\\s+"
                    + "([\\d,]+)\\s+([\\d,]+)\\s+([\\d,]+)\\s+([\\d,]+)\\s+"
                    + "([\\d,]+)\\s+([\\d,]+)\\s+([\\d,]+)\\s+(\\d+)"
    );

    private final Map<String, List<FlashCandleDto>> symbolCache =
            new ConcurrentHashMap<>();

    private final Set<String> symbols = ConcurrentHashMap.newKeySet();

    private final PathMatchingResourcePatternResolver resolver =
            new PathMatchingResourcePatternResolver();


    @PostConstruct
    void warmDefaultSymbol() {
        try {
            Resource[] resources = listHistoResources();
            for (Resource resource : resources) {
                indexSymbolsFromResource(resource);
            }
            if (!symbols.contains(DEFAULT_SYMBOL)) {
                symbols.add(DEFAULT_SYMBOL);
            }
            loadSymbol(DEFAULT_SYMBOL);
            log.info(
                    "Histo cotation ready: {} symbols indexed, {} candles for {}",
                    symbols.size(),
                    symbolCache.getOrDefault(DEFAULT_SYMBOL, List.of()).size(),
                    DEFAULT_SYMBOL
            );
        } catch (Exception exception) {
            log.warn(
                    "Histo cotation warm-up failed: {}",
                    exception.getMessage()
            );
        }
    }


    public List<String> listSymbols() {
        TreeSet<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        sorted.addAll(symbols);
        return List.copyOf(sorted);
    }


    public List<FlashCandleDto> getCandles(String symbol) {
        String key = normalizeSymbol(symbol);
        List<FlashCandleDto> candles = symbolCache.get(key);
        if (candles == null) {
            candles = loadSymbol(key);
        }
        if (candles.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Aucune cotation historique pour le symbole " + key
            );
        }
        return candles;
    }


    /**
     * Canonical multiplayer stream: BIAT daily sessions from 2018 through 2026.
     */
    public List<FlashCandleDto> getPrimaryDataset() {
        return getDatasetForSymbol(DEFAULT_SYMBOL);
    }


    /**
     * 2018–2026 daily sessions for the admin-selected Tunisian ticker.
     */
    public List<FlashCandleDto> getDatasetForSymbol(String symbol) {
        String key = normalizeSymbol(
                symbol == null || symbol.isBlank() ? DEFAULT_SYMBOL : symbol
        );
        List<FlashCandleDto> all = getCandles(key);
        List<FlashCandleDto> filtered = all.stream()
                .filter(c -> {
                    int year = parseIsoDate(c.date()).getYear();
                    return year >= PRIMARY_DATASET_FROM_YEAR
                            && year <= PRIMARY_DATASET_TO_YEAR;
                })
                .toList();
        if (filtered.size() < DEFAULT_WINDOW_SIZE + 1) {
            return all;
        }
        return filtered;
    }


    /**
     * Returns ~{@ windowSize} consecutive sessions starting at
     * {@code windowStart} when provided, otherwise a mid-series window.
     */
    public List<FlashCandleDto> getWindow(
            String symbol,
            Integer year,
            String windowStart,
            int windowSize
    ) {
        List<FlashCandleDto> all = getCandles(symbol);
        List<FlashCandleDto> filtered = all;

        if (year != null) {
            filtered = all.stream()
                    .filter(c -> parseIsoDate(c.date()).getYear() == year)
                    .toList();
            if (filtered.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Aucune séance pour " + symbol + " en " + year
                );
            }
        }

        int size = Math.max(2, windowSize);
        int startIndex = 0;

        if (windowStart != null && !windowStart.isBlank()) {
            LocalDate start = parseFlexibleDate(windowStart.trim());
            startIndex = 0;
            for (int i = 0; i < filtered.size(); i++) {
                if (!parseIsoDate(filtered.get(i).date()).isBefore(start)) {
                    startIndex = i;
                    break;
                }
                startIndex = i;
            }
        } else {
            // Prefer a dense mid window so demos are not stuck on sparse edges.
            startIndex = Math.max(0, (filtered.size() - size) / 2);
        }

        if (startIndex + size > filtered.size()) {
            startIndex = Math.max(0, filtered.size() - size);
        }

        return List.copyOf(
                filtered.subList(
                        startIndex,
                        Math.min(filtered.size(), startIndex + size)
                )
        );
    }


    private List<FlashCandleDto> loadSymbol(String symbol) {
        String key = normalizeSymbol(symbol);
        try {
            Map<LocalDate, FlashCandleDto> byDate = new LinkedHashMap<>();
            for (Resource resource : listHistoResources()) {
                parseResourceInto(resource, key, byDate);
            }
            List<FlashCandleDto> candles = byDate.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .toList();
            symbolCache.put(key, candles);
            if (!candles.isEmpty()) {
                symbols.add(key);
            }
            return candles;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Impossible de lire les fichiers histo_cotation: "
                            + exception.getMessage()
            );
        }
    }


    private Resource[] listHistoResources() throws Exception {
        Resource[] resources = resolver.getResources(
                "classpath*:histo_cotation_*.{txt,csv}"
        );
        List<Resource> usable = new ArrayList<>();
        for (Resource resource : resources) {
            String name = resource.getFilename();
            if (name == null) {
                continue;
            }
            // Skip accidental duplicates like "histo_cotation_2022 (1).csv".
            if (name.contains("(") || name.contains(")")) {
                continue;
            }
            usable.add(resource);
        }
        usable.sort(Comparator.comparing(
                r -> r.getFilename() == null ? "" : r.getFilename()
        ));
        return usable.toArray(Resource[]::new);
    }


    private void indexSymbolsFromResource(Resource resource) throws Exception {
        String name = resource.getFilename();
        if (name == null) {
            return;
        }
        boolean csv = name.toLowerCase(Locale.ROOT).endsWith(".csv");
        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                resource.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                )
        ) {
            String line;
            int seen = 0;
            while ((line = reader.readLine()) != null && seen < 5_000) {
                if (line.isBlank() || line.startsWith("SEANCE")
                        || line.startsWith("--------")) {
                    continue;
                }
                String lib = csv ? extractCsvLib(line) : extractTxtLib(line);
                if (lib != null && !lib.isBlank()) {
                    symbols.add(normalizeSymbol(lib));
                    seen++;
                }
            }
        }
    }


    private void parseResourceInto(
            Resource resource,
            String symbol,
            Map<LocalDate, FlashCandleDto> byDate
    ) throws Exception {
        String name = resource.getFilename();
        if (name == null) {
            return;
        }
        boolean csv = name.toLowerCase(Locale.ROOT).endsWith(".csv");
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
                if (line.isBlank() || line.startsWith("SEANCE")
                        || line.startsWith("--------")) {
                    continue;
                }
                FlashCandleDto candle = csv
                        ? parseCsvRow(line, symbol)
                        : parseTxtRow(line, symbol);
                if (candle != null) {
                    byDate.put(parseIsoDate(candle.date()), candle);
                }
            }
        }
    }


    private FlashCandleDto parseCsvRow(String line, String symbol) {
        String[] parts = line.split(";");
        if (parts.length < 9) {
            return null;
        }
        String lib = parts[3].trim();
        if (!matchesSymbol(lib, parts[2].trim(), symbol)) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(parts[0].trim(), CSV_DATE);
            BigDecimal open = parseDecimal(parts[4]);
            BigDecimal close = parseDecimal(parts[5]);
            BigDecimal low = parseDecimal(parts[6]);
            BigDecimal high = parseDecimal(parts[7]);
            long volume = parseLong(parts[8]);
            if (open.signum() <= 0 && close.signum() <= 0) {
                return null;
            }
            if (open.signum() <= 0) {
                open = close;
            }
            if (high.signum() <= 0) {
                high = open.max(close);
            }
            if (low.signum() <= 0) {
                low = open.min(close);
            }
            return new FlashCandleDto(
                    date.toString(),
                    open,
                    high,
                    low,
                    close.signum() <= 0 ? open : close,
                    volume
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }


    private FlashCandleDto parseTxtRow(String line, String symbol) {
        Matcher matcher = TXT_ROW.matcher(line.trim());
        if (!matcher.find()) {
            return null;
        }
        String code = matcher.group(3);
        String lib = matcher.group(4).trim();
        if (!matchesSymbol(lib, code, symbol)) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(matcher.group(1), TXT_DATE);
            // Groups: 5 ref, 6 veille, 7 open, 8 dernier, 9 close, 10 low, 11 high, 12 qty
            BigDecimal open = parseDecimal(matcher.group(7));
            BigDecimal close = parseDecimal(matcher.group(9));
            BigDecimal low = parseDecimal(matcher.group(10));
            BigDecimal high = parseDecimal(matcher.group(11));
            long volume = parseLong(matcher.group(12));
            if (open.signum() <= 0 && close.signum() <= 0) {
                return null;
            }
            if (open.signum() <= 0) {
                open = close;
            }
            if (high.signum() <= 0) {
                high = open.max(close);
            }
            if (low.signum() <= 0) {
                low = open.min(close);
            }
            return new FlashCandleDto(
                    date.toString(),
                    open,
                    high,
                    low,
                    close.signum() <= 0 ? open : close,
                    volume
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }


    private static String extractCsvLib(String line) {
        String[] parts = line.split(";");
        if (parts.length < 4) {
            return null;
        }
        return parts[3].trim();
    }


    private static String extractTxtLib(String line) {
        Matcher matcher = TXT_ROW.matcher(line.trim());
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(4).trim();
    }


    private static boolean matchesSymbol(
            String lib,
            String code,
            String symbol
    ) {
        String normalizedLib = normalizeSymbol(lib);
        String target = normalizeSymbol(symbol);
        if (normalizedLib.equals(target)) {
            return true;
        }
        // BIAT ISIN / legacy code suffix
        if ("BIAT".equals(target)
                && (code.contains("180045") || normalizedLib.equals("BIAT"))) {
            return true;
        }
        return false;
    }


    private static String normalizeSymbol(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }


    private static BigDecimal parseDecimal(String raw) {
        String cleaned = raw.trim().replace(',', '.');
        if (cleaned.isEmpty() || cleaned.equals(".")) {
            return BigDecimal.ZERO;
        }
        // Leading comma forms like ",99"
        if (cleaned.startsWith(".")) {
            cleaned = "0" + cleaned;
        }
        return new BigDecimal(cleaned);
    }


    private static long parseLong(String raw) {
        String cleaned = raw.trim().replace(',', '.');
        int dot = cleaned.indexOf('.');
        if (dot >= 0) {
            cleaned = cleaned.substring(0, dot);
        }
        if (cleaned.isEmpty()) {
            return 0L;
        }
        return Long.parseLong(cleaned);
    }


    private static LocalDate parseIsoDate(String iso) {
        return LocalDate.parse(iso);
    }


    private static LocalDate parseFlexibleDate(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(raw, CSV_DATE);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(raw, TXT_DATE);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Date de fenêtre invalide: " + raw
            );
        }
    }
}
