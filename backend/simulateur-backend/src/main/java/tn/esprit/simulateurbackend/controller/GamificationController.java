package tn.esprit.simulateurbackend.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import tn.esprit.simulateurbackend.dto.GamificationCandleResponse;
import tn.esprit.simulateurbackend.dto.GamificationOrderRequest;
import tn.esprit.simulateurbackend.dto.GamificationPortfolioResponse;
import tn.esprit.simulateurbackend.service.GamificationService;

import java.util.List;

/*
 * API isolée : ne partage aucun service avec le carnet live.
 */
@RestController
@RequestMapping("/api/gamification")
public class GamificationController {

    private final GamificationService gamificationService;


    public GamificationController(
            GamificationService gamificationService
    ) {
        this.gamificationService = gamificationService;
    }


    @GetMapping("/data/{event}")
    public ResponseEntity<List<GamificationCandleResponse>>
    getHistoricalData(
            @PathVariable String event
    ) {

        return ResponseEntity.ok(
                gamificationService.getHistoricalData(event)
        );
    }


    @PostMapping("/sessions/{event}/start")
    public ResponseEntity<GamificationPortfolioResponse>
    startChallenge(
            @PathVariable String event,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                gamificationService.startChallenge(
                        authentication.getName(),
                        event
                )
        );
    }


    @GetMapping("/sessions/{event}")
    public ResponseEntity<GamificationPortfolioResponse>
    getSession(
            @PathVariable String event,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                gamificationService.getSession(
                        authentication.getName(),
                        event
                )
        );
    }


    @PostMapping("/sessions/{event}/advance")
    public ResponseEntity<GamificationPortfolioResponse>
    advanceWeek(
            @PathVariable String event,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                gamificationService.advanceWeek(
                        authentication.getName(),
                        event
                )
        );
    }


    @PostMapping("/sessions/{event}/orders")
    public ResponseEntity<GamificationPortfolioResponse>
    placeOrder(
            @PathVariable String event,
            @Valid @RequestBody GamificationOrderRequest request,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                gamificationService.placeMarketOrder(
                        authentication.getName(),
                        event,
                        request
                )
        );
    }
}
