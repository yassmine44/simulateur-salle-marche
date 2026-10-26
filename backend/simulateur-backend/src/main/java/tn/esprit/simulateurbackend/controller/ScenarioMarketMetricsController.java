package tn.esprit.simulateurbackend.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.simulateurbackend.repository.AssetRepository;
import tn.esprit.simulateurbackend.service.MarketMakerService;

@RestController
@RequestMapping("/api/admin/scenarios")
public class ScenarioMarketMetricsController {
    private final MarketMakerService marketMakerService;
    private final AssetRepository assetRepository;

    public ScenarioMarketMetricsController(MarketMakerService marketMakerService,
                                           AssetRepository assetRepository) {
        this.marketMakerService = marketMakerService;
        this.assetRepository = assetRepository;
    }

    @GetMapping("/market/{symbol}")
    public MarketSnapshot market(@PathVariable String symbol) {
        var asset = assetRepository.findBySymbolIgnoreCaseAndActiveTrue(symbol.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Actif introuvable ou inactif"));
        BigDecimal referencePrice = asset.getReferencePrice();
        BigDecimal midPrice = marketMakerService.currentQuotedMidPrice(asset.getSymbol());
        BigDecimal variationPercent = null;
        if (midPrice != null && referencePrice != null && referencePrice.signum() > 0) {
            variationPercent = midPrice.subtract(referencePrice)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(referencePrice, 2, RoundingMode.HALF_UP);
        }
        return new MarketSnapshot(asset.getSymbol(), referencePrice, midPrice,
                variationPercent, Instant.now());
    }

    // capturedAt is the observation time, not the time of the last trade or quote.
    public record MarketSnapshot(String symbol, BigDecimal referencePrice,
                                 BigDecimal quotedMidPrice, BigDecimal variationPercent,
                                 Instant capturedAt) {}
}
