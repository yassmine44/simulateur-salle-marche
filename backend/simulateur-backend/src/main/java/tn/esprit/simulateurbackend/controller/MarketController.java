package tn.esprit.simulateurbackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import tn.esprit.simulateurbackend.dto.MarketOverviewResponse;
import tn.esprit.simulateurbackend.service.MarketDataService;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketDataService marketDataService;

    public MarketController(
            MarketDataService marketDataService
    ) {
        this.marketDataService =
                marketDataService;
    }

    /*
     * Dernier cours, statistiques 24 h, dernières
     * exécutions du marché et chandeliers d'un actif.
     *
     * interval : 1m (défaut), 5m ou 15m.
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<MarketOverviewResponse> getOverview(
            @PathVariable String symbol,
            @RequestParam(required = false) String interval
    ) {

        return ResponseEntity.ok(
                marketDataService.getOverview(
                        symbol,
                        interval
                )
        );
    }
}
