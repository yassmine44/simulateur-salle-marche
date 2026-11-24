package tn.esprit.simulateurbackend.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;

import tn.esprit.simulateurbackend.dto.actuarial.ComparisonRequest;
import tn.esprit.simulateurbackend.dto.actuarial.ComparisonResponse;
import tn.esprit.simulateurbackend.dto.actuarial.QuoteHistoryItem;
import tn.esprit.simulateurbackend.dto.actuarial.QuoteRequest;
import tn.esprit.simulateurbackend.dto.actuarial.QuoteResponse;
import tn.esprit.simulateurbackend.service.ActuarialService;

/**
 * Module d'actuariat (cahier des charges §3.7) : tarification d'assurance,
 * probabilité de sinistre, marge de prime et explications XAI (§3.6).
 * Réservé aux utilisateurs authentifiés.
 */
@RestController
@RequestMapping("/api/actuarial")
public class ActuarialController {

    private final ActuarialService actuarialService;

    public ActuarialController(ActuarialService actuarialService) {
        this.actuarialService = actuarialService;
    }

    /** Définition du formulaire : libellés, bornes et valeurs acceptées. */
    @GetMapping("/metadata")
    public ResponseEntity<JsonNode> metadata(Authentication authentication) {
        requireUser(authentication);
        return ResponseEntity.ok(actuarialService.metadata());
    }

    /** État du moteur : versions des modèles et hypothèses en vigueur. */
    @GetMapping("/status")
    public ResponseEntity<JsonNode> status(Authentication authentication) {
        requireUser(authentication);
        return ResponseEntity.ok(actuarialService.status());
    }

    /** Tarifie un profil, explique la prime et trace le devis. */
    @PostMapping("/quotes")
    public ResponseEntity<QuoteResponse> quote(
            Authentication authentication,
            @Valid @RequestBody QuoteRequest request
    ) {
        return ResponseEntity.ok(actuarialService.quote(requireUser(authentication), request));
    }

    /** Historique des devis de l'utilisateur connecté, du plus récent au plus ancien. */
    @GetMapping("/quotes/me")
    public ResponseEntity<List<QuoteHistoryItem>> myQuotes(
            Authentication authentication,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(actuarialService.history(requireUser(authentication), limit));
    }

    /** Explique l'écart de prime entre deux profils. */
    @PostMapping("/comparisons")
    public ResponseEntity<ComparisonResponse> compare(
            Authentication authentication,
            @Valid @RequestBody ComparisonRequest request
    ) {
        requireUser(authentication);
        return ResponseEntity.ok(actuarialService.compare(request));
    }

    /** Rapports d'évaluation : frequency, severity, pricing, explainability. */
    @GetMapping("/reports/{name}")
    public ResponseEntity<JsonNode> report(Authentication authentication, @PathVariable String name) {
        requireUser(authentication);
        return ResponseEntity.ok(actuarialService.report(name));
    }

    /** Fiches des modèles : données, métriques, versions, environnement. */
    @GetMapping("/model-cards")
    public ResponseEntity<JsonNode> modelCards(Authentication authentication) {
        requireUser(authentication);
        return ResponseEntity.ok(actuarialService.modelCards());
    }

    private static String requireUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise.");
        }
        return authentication.getName();
    }
}
