package tn.esprit.simulateurbackend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import tn.esprit.simulateurbackend.dto.actuarial.AdminActuarialOverview;
import tn.esprit.simulateurbackend.dto.actuarial.CurrencyRequest;
import tn.esprit.simulateurbackend.dto.actuarial.PricingAssumptionsDto;
import tn.esprit.simulateurbackend.entity.ActuarialAdminAction;
import tn.esprit.simulateurbackend.repository.ActuarialAdminActionRepository;
import tn.esprit.simulateurbackend.repository.ActuarialQuoteRepository;

/**
 * Administration du module d'actuariat (cahier des charges §5) : état des
 * modèles, paramètres des simulations, rechargement, usage et traçabilité.
 */
@Service
public class AdminActuarialService {

    public static final int MAX_LIST = 200;
    private static final int RECENT_ACTIONS = 20;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ActuarialPricingClient client;
    private final ActuarialQuoteRepository quotes;
    private final ActuarialAdminActionRepository actions;
    private final Clock clock;

    @Autowired
    public AdminActuarialService(
            ActuarialPricingClient client,
            ActuarialQuoteRepository quotes,
            ActuarialAdminActionRepository actions
    ) {
        this(client, quotes, actions, Clock.systemUTC());
    }

    AdminActuarialService(
            ActuarialPricingClient client,
            ActuarialQuoteRepository quotes,
            ActuarialAdminActionRepository actions,
            Clock clock
    ) {
        this.client = client;
        this.quotes = quotes;
        this.actions = actions;
        this.clock = clock;
    }

    /**
     * Vue d'ensemble. Si le moteur est indisponible, les statistiques de la
     * base restent consultables et la raison est indiquée.
     */
    @Transactional(readOnly = true)
    public AdminActuarialOverview overview() {
        JsonNode engine = null;
        JsonNode cards = null;
        String engineError = null;
        try {
            engine = client.status();
            cards = client.modelCards();
        } catch (ResponseStatusException unavailable) {
            engineError = unavailable.getReason();
        }

        Instant now = Instant.now(clock);
        var usage = new AdminActuarialOverview.Usage(
                quotes.count(),
                quotes.countByCreatedAtAfter(now.minus(Duration.ofDays(30))),
                quotes.countDistinctUsers(),
                quotes.usageByModelVersion().stream()
                        .map(v -> new AdminActuarialOverview.VersionUsage(
                                v.getVersion(), v.getQuotes(), v.getAverageCommercialPremium(), v.getLastQuoteAt()))
                        .toList()
        );
        List<AdminActuarialOverview.Action> recent = actions
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, RECENT_ACTIONS))
                .stream()
                .map(AdminActuarialOverview.Action::from)
                .toList();
        return new AdminActuarialOverview(engine, cards, engineError, usage, recent);
    }

    /** Remplace les hypothèses commerciales et trace l'avant/après. */
    @Transactional
    public JsonNode updateAssumptions(String actorEmail, PricingAssumptionsDto assumptions) {
        JsonNode result = client.updateAssumptions(assumptions);
        JsonNode change = JSON.createObjectNode()
                .<tools.jackson.databind.node.ObjectNode>set("previous", result.path("previous"))
                .set("current", result.path("current"));
        record(ActuarialAdminAction.Type.ASSUMPTIONS_UPDATED, actorEmail, change.toString());
        return result;
    }

    /** Change la devise d'affichage et trace l'avant/après. */
    @Transactional
    public JsonNode updateCurrency(String actorEmail, CurrencyRequest currency) {
        JsonNode result = client.updateCurrency(currency);
        JsonNode change = JSON.createObjectNode()
                .<tools.jackson.databind.node.ObjectNode>set("previous", result.path("previous"))
                .set("current", result.path("current"));
        record(ActuarialAdminAction.Type.CURRENCY_UPDATED, actorEmail, change.toString());
        return result;
    }

    /** Recharge les modèles (après un réentraînement) et trace les versions servies. */
    @Transactional
    public JsonNode reload(String actorEmail) {
        JsonNode status = client.reload();
        record(ActuarialAdminAction.Type.MODELS_RELOADED, actorEmail, status.path("modelVersions").toString());
        return status;
    }

    @Transactional(readOnly = true)
    public List<AdminActuarialOverview.QuoteItem> recentQuotes(int limit) {
        int size = Math.max(1, Math.min(limit, MAX_LIST));
        return quotes.findAllByOrderByCreatedAtDesc(PageRequest.of(0, size)).stream()
                .map(AdminActuarialOverview.QuoteItem::from)
                .toList();
    }

    private void record(ActuarialAdminAction.Type type, String actorEmail, String details) {
        if (actorEmail == null || actorEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise.");
        }
        actions.save(ActuarialAdminAction.of(
                type, actorEmail.trim().toLowerCase(Locale.ROOT), details, Instant.now(clock)
        ));
    }
}
