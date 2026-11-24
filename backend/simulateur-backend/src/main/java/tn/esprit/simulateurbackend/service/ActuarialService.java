package tn.esprit.simulateurbackend.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;

import tn.esprit.simulateurbackend.dto.actuarial.ChainLadderRequest;
import tn.esprit.simulateurbackend.dto.actuarial.ComparisonRequest;
import tn.esprit.simulateurbackend.dto.actuarial.ComparisonResponse;
import tn.esprit.simulateurbackend.dto.actuarial.QuoteHistoryItem;
import tn.esprit.simulateurbackend.dto.actuarial.QuoteRequest;
import tn.esprit.simulateurbackend.dto.actuarial.QuoteResponse;
import tn.esprit.simulateurbackend.dto.actuarial.StressRequest;
import tn.esprit.simulateurbackend.entity.ActuarialQuote;
import tn.esprit.simulateurbackend.repository.ActuarialQuoteRepository;

/**
 * Cas d'usage du module d'actuariat : tarification, explication,
 * comparaison, historique et rapports d'évaluation.
 */
@Service
public class ActuarialService {

    public static final Set<String> REPORTS =
            Set.of("frequency", "severity", "pricing", "explainability", "reserving");
    public static final int MAX_HISTORY = 100;

    private final ActuarialPricingClient client;
    private final ActuarialQuoteRepository repository;
    private final Clock clock;

    @Autowired
    public ActuarialService(ActuarialPricingClient client, ActuarialQuoteRepository repository) {
        this(client, repository, Clock.systemUTC());
    }

    ActuarialService(ActuarialPricingClient client, ActuarialQuoteRepository repository, Clock clock) {
        this.client = client;
        this.repository = repository;
        this.clock = clock;
    }

    /** Tarifie le profil et trace le devis au nom de l'utilisateur. */
    @Transactional
    public QuoteResponse quote(String userEmail, QuoteRequest request) {
        QuoteResponse response = client.quote(request);
        repository.save(ActuarialQuote.record(
                normalize(userEmail), request.profile(), response, Instant.now(clock)
        ));
        return response;
    }

    @Transactional(readOnly = true)
    public List<QuoteHistoryItem> history(String userEmail, int limit) {
        int size = Math.max(1, Math.min(limit, MAX_HISTORY));
        return repository
                .findByUserEmailOrderByCreatedAtDesc(normalize(userEmail), PageRequest.of(0, size))
                .stream()
                .map(QuoteHistoryItem::from)
                .toList();
    }

    public ComparisonResponse compare(ComparisonRequest request) {
        return client.compare(request);
    }

    /** Stress test du portefeuille tarifé (chocs sur les sinistres). */
    public JsonNode stress(StressRequest request) {
        return client.stress(request);
    }

    /** Simulation de provisionnement sur un triangle fourni par l'utilisateur. */
    public JsonNode chainLadder(ChainLadderRequest request) {
        return client.chainLadder(request);
    }

    public JsonNode metadata() {
        return client.metadata();
    }

    public JsonNode status() {
        return client.status();
    }

    public JsonNode report(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (!REPORTS.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Rapport inconnu. Rapports disponibles : frequency, severity, pricing, explainability, reserving."
            );
        }
        return client.report(normalized);
    }

    public JsonNode modelCards() {
        return client.modelCards();
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
