package tn.esprit.simulateurbackend.controller;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import tn.esprit.simulateurbackend.dto.BuyingPowerResponse;
import tn.esprit.simulateurbackend.dto.PerformanceResponse;
import tn.esprit.simulateurbackend.dto.PortfolioResponse;
import tn.esprit.simulateurbackend.dto.RebalanceExecutionResponse;
import tn.esprit.simulateurbackend.dto.RebalancePlanResponse;
import tn.esprit.simulateurbackend.dto.RebalanceRequest;
import tn.esprit.simulateurbackend.dto.StatementResponse;
import tn.esprit.simulateurbackend.service.OrderBookLocks;
import tn.esprit.simulateurbackend.service.PortfolioAnalyticsService;
import tn.esprit.simulateurbackend.service.PortfolioService;
import tn.esprit.simulateurbackend.service.RebalanceService;
import tn.esprit.simulateurbackend.service.StatementService;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final OrderBookLocks orderBookLocks;
    private final PortfolioAnalyticsService analyticsService;
    private final RebalanceService rebalanceService;
    private final StatementService statementService;

    public PortfolioController(
            PortfolioService portfolioService,
            OrderBookLocks orderBookLocks,
            PortfolioAnalyticsService analyticsService,
            RebalanceService rebalanceService,
            StatementService statementService
    ) {
        this.portfolioService = portfolioService;
        this.orderBookLocks = orderBookLocks;
        this.analyticsService = analyticsService;
        this.rebalanceService = rebalanceService;
        this.statementService = statementService;
    }


    /*
     * Liquidités, positions, résultats, répartition,
     * indicateurs de risque et historique.
     *
     * historyDays : 7 (défaut) ou 30.
     */
    @GetMapping("/me")
    public ResponseEntity<PortfolioResponse> getMyPortfolio(
            @RequestParam(required = false) Integer historyDays,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                portfolioService.getPortfolio(
                        authentication.getName(),
                        historyDays
                )
        );
    }


    /*
     * Liquidités disponibles et titres disponibles sur un actif :
     * utilisé par le formulaire d'ordre.
     */
    @GetMapping("/me/buying-power")
    public ResponseEntity<BuyingPowerResponse> getMyBuyingPower(
            @RequestParam(required = false) String symbol,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                portfolioService.getBuyingPower(
                        authentication.getName(),
                        symbol
                )
        );
    }


    /*
     * Statistiques de trader, contribution par actif, qualité
     * d'exécution et comparaison à la référence « acheter et conserver ».
     */
    @GetMapping("/me/performance")
    public ResponseEntity<PerformanceResponse> getMyPerformance(
            @RequestParam(required = false) Integer historyDays,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                analyticsService.getPerformance(
                        authentication.getName(),
                        historyDays
                )
        );
    }


    /*
     * Ordres nécessaires pour atteindre une répartition cible
     * (rien n'est envoyé).
     */
    @PostMapping("/me/rebalance/plan")
    public ResponseEntity<RebalancePlanResponse> planRebalance(
            @Valid @RequestBody RebalanceRequest request,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                rebalanceService.plan(authentication.getName(), request)
        );
    }


    /*
     * Recalcule le plan et envoie les ordres (ventes d'abord).
     */
    @PostMapping("/me/rebalance/execute")
    public ResponseEntity<RebalanceExecutionResponse> executeRebalance(
            @Valid @RequestBody RebalanceRequest request,
            Authentication authentication
    ) {

        String email = authentication.getName();

        return ResponseEntity.ok(
                orderBookLocks.withLock(
                        OrderController.userLockKey(email),
                        () -> rebalanceService.execute(email, request)
                )
        );
    }


    /*
     * Relevé de compte : from / to au format AAAA-MM-JJ
     * (par défaut : mois en cours).
     */
    @GetMapping("/me/statement")
    public ResponseEntity<StatementResponse> getMyStatement(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                statementService.statement(authentication.getName(), from, to)
        );
    }


    /*
     * Réinitialise le portefeuille : ordres ouverts annulés,
     * capital de départ restauré, historique conservé.
     */
    @PostMapping("/me/reset")
    public ResponseEntity<PortfolioResponse> resetMyPortfolio(
            Authentication authentication
    ) {

        String email = authentication.getName();

        return ResponseEntity.ok(
                orderBookLocks.withLock(
                        OrderController.userLockKey(email),
                        () -> portfolioService.reset(email)
                )
        );
    }
}
