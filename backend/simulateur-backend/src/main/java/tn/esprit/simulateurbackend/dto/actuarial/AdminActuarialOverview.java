package tn.esprit.simulateurbackend.dto.actuarial;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import tools.jackson.databind.JsonNode;

import tn.esprit.simulateurbackend.entity.ActuarialAdminAction;
import tn.esprit.simulateurbackend.entity.ActuarialQuote;

/**
 * Vue d'administration du module d'actuariat.
 *
 * @param engine        état du moteur (versions, hypothèses), ou {@code null} s'il est indisponible
 * @param modelCards    fiches des modèles, ou {@code null}
 * @param engineError   raison de l'indisponibilité du moteur, sinon {@code null}
 * @param usage         statistiques des devis enregistrés
 * @param recentActions dernières actions d'administration
 */
public record AdminActuarialOverview(
        JsonNode engine,
        JsonNode modelCards,
        String engineError,
        Usage usage,
        List<Action> recentActions
) {

    public record Usage(
            long totalQuotes,
            long quotesLast30Days,
            long distinctUsers,
            List<VersionUsage> byModelVersion
    ) {
    }

    public record VersionUsage(
            String version,
            long quotes,
            Double averageCommercialPremium,
            Instant lastQuoteAt
    ) {
    }

    public record Action(Long id, String type, String actorEmail, Instant createdAt, String details) {

        public static Action from(ActuarialAdminAction action) {
            return new Action(
                    action.getId(), action.getType().name(), action.getActorEmail(),
                    action.getCreatedAt(), action.getDetails()
            );
        }
    }

    /** Devis vu par l'administrateur (avec l'utilisateur, pour la traçabilité). */
    public record QuoteItem(
            Long id,
            Instant createdAt,
            String userEmail,
            int driverAge,
            int bonusMalus,
            String area,
            BigDecimal purePremium,
            BigDecimal commercialPremium,
            double expenseRatio,
            double profitRatio,
            String frequencyModelVersion,
            String currency
    ) {

        public static QuoteItem from(ActuarialQuote quote) {
            return new QuoteItem(
                    quote.getId(), quote.getCreatedAt(), quote.getUserEmail(), quote.getDriverAge(),
                    quote.getBonusMalus(), quote.getArea(), quote.getPurePremium(),
                    quote.getCommercialPremium(), quote.getExpenseRatio(), quote.getProfitRatio(),
                    quote.getFrequencyModelVersion(), quote.getCurrency()
            );
        }
    }
}
