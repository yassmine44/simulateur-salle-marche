package tn.esprit.simulateurbackend.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

import tn.esprit.simulateurbackend.dto.actuarial.AdminActuarialOverview;
import tn.esprit.simulateurbackend.dto.actuarial.CurrencyRequest;
import tn.esprit.simulateurbackend.dto.actuarial.PricingAssumptionsDto;
import tn.esprit.simulateurbackend.service.AdminActuarialService;

/**
 * Administration du module d'actuariat. Réservé au rôle ADMIN : la règle
 * globale {@code /api/admin/**} de SecurityConfig est doublée d'un
 * {@code @PreAuthorize} (défense en profondeur).
 */
@RestController
@RequestMapping("/api/admin/actuarial")
@PreAuthorize("hasRole('ADMIN')")
public class AdminActuarialController {

    private final AdminActuarialService adminService;

    public AdminActuarialController(AdminActuarialService adminService) {
        this.adminService = adminService;
    }

    /** État des modèles, statistiques d'usage et dernières actions. */
    @GetMapping("/overview")
    public ResponseEntity<AdminActuarialOverview> overview() {
        return ResponseEntity.ok(adminService.overview());
    }

    /** Hypothèses commerciales appliquées par défaut à tous les devis. */
    @PutMapping("/assumptions")
    public ResponseEntity<JsonNode> updateAssumptions(
            Authentication authentication,
            @Valid @RequestBody PricingAssumptionsDto assumptions
    ) {
        return ResponseEntity.ok(adminService.updateAssumptions(authentication.getName(), assumptions));
    }

    /** Devise d'affichage des montants et taux de conversion depuis l'euro. */
    @PutMapping("/currency")
    public ResponseEntity<JsonNode> updateCurrency(
            Authentication authentication,
            @Valid @RequestBody CurrencyRequest currency
    ) {
        return ResponseEntity.ok(adminService.updateCurrency(authentication.getName(), currency));
    }

    /** Recharge les modèles après un réentraînement. */
    @PostMapping("/reload")
    public ResponseEntity<JsonNode> reload(Authentication authentication) {
        return ResponseEntity.ok(adminService.reload(authentication.getName()));
    }

    /** Derniers devis de tous les utilisateurs (traçabilité). */
    @GetMapping("/quotes")
    public ResponseEntity<List<AdminActuarialOverview.QuoteItem>> quotes(
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(adminService.recentQuotes(limit));
    }
}
