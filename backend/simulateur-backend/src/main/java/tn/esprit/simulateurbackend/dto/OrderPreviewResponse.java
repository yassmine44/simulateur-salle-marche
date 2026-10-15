package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/*
 * Aperçu d'un ordre avant envoi : ce qui serait exécuté tout
 * de suite, à quel prix, ce qui resterait en attente, et l'effet
 * sur le portefeuille. Rien n'est enregistré.
 */
public record OrderPreviewResponse(

        // false : l'ordre serait refusé (voir refusalReason).
        boolean accepted,

        String refusalReason,

        // Points d'attention, même si l'ordre est accepté.
        List<String> warnings,

        String currency,

        // ---- Marché au moment de l'aperçu ----

        BigDecimal bestBid,

        BigDecimal bestAsk,

        BigDecimal midPrice,

        BigDecimal lastPrice,

        // ---- Exécution immédiate estimée ----

        BigDecimal immediateQuantity,

        BigDecimal averagePrice,

        // Montant des exécutions immédiates (hors attente).
        BigDecimal immediateAmount,

        // Écart du prix moyen au prix milieu, en points de base (coût si > 0).
        BigDecimal slippageBps,

        // Nombre de niveaux de prix du carnet consommés.
        int levelsConsumed,

        // ---- Reste ----

        // Reste placé dans le carnet (ordre limite GTC / DAY).
        BigDecimal restingQuantity,

        // Reste annulé (ordre au marché, IOC, FOK).
        BigDecimal cancelledQuantity,

        // Ordre conditionnel : seuil et distance au cours.
        BigDecimal triggerPrice,

        BigDecimal triggerDistancePercent,

        Instant expiresAt,

        // ---- Portefeuille ----

        BigDecimal availableCashBefore,

        // Liquidités disponibles après exécution et réservation.
        BigDecimal availableCashAfter,

        BigDecimal heldQuantityBefore,

        BigDecimal heldQuantityAfter,

        // Poids de la position dans le portefeuille après exécution immédiate.
        BigDecimal positionWeightAfterPercent,

        // ---- Bracket : gain et perte possibles ----

        BigDecimal potentialGain,

        BigDecimal potentialLoss,

        // Gain possible / perte possible.
        BigDecimal rewardRiskRatio
) {
}
