package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/*
 * Vue complète du portefeuille virtuel d'un utilisateur,
 * recalculée à partir de ses transactions et de ses ordres ouverts.
 */
public record PortfolioResponse(

        String currency,

        // Capital virtuel de départ.
        BigDecimal initialCapital,

        // Début du portefeuille : null = depuis la création du compte.
        Instant startedAt,

        int resetCount,

        // Les ordres sont contrôlés (fonds / titres suffisants).
        boolean limitsEnforced,

        // ---- Liquidités ----

        // Capital - achats + ventes.
        BigDecimal cash,

        // Montant engagé par les ordres d'achat en attente.
        BigDecimal reservedCash,

        // Utilisable pour un nouvel achat.
        BigDecimal availableCash,

        // ---- Valorisation ----

        BigDecimal positionsValue,

        // Liquidités + valeur des positions au dernier cours.
        BigDecimal totalValue,

        BigDecimal totalPnl,

        BigDecimal totalPnlPercent,

        BigDecimal realizedPnl,

        BigDecimal unrealizedPnl,

        long tradeCount,

        // ---- Détail ----

        List<Position> openPositions,

        List<ClosedPosition> closedPositions,

        List<AllocationSlice> allocation,

        Risk risk,

        int historyDays,

        List<EquityPoint> history,

        Instant generatedAt
) {

    public record Position(

            String assetSymbol,

            String assetName,

            String assetType,

            // > 0 : position acheteuse ; < 0 : position vendeuse
            // (uniquement des données antérieures au contrôle des ordres).
            BigDecimal quantity,

            String direction,

            // Prix de revient moyen pondéré.
            BigDecimal averagePrice,

            BigDecimal lastPrice,

            // Quantité x prix de revient.
            BigDecimal costBasis,

            // Quantité x dernier cours.
            BigDecimal marketValue,

            BigDecimal unrealizedPnl,

            BigDecimal unrealizedPnlPercent,

            BigDecimal realizedPnl,

            // Part de la valeur totale du portefeuille.
            BigDecimal weightPercent,

            // Quantité engagée dans des ordres de vente en attente.
            BigDecimal reservedQuantity,

            BigDecimal availableQuantity,

            Instant openedAt
    ) {
    }

    public record ClosedPosition(

            String assetSymbol,

            String assetName,

            BigDecimal boughtQuantity,

            BigDecimal soldQuantity,

            BigDecimal realizedPnl
    ) {
    }

    /*
     * Répartition par classe d'actifs (liquidités comprises).
     */
    public record AllocationSlice(

            String key,

            String label,

            BigDecimal value,

            BigDecimal percent
    ) {
    }

    public record Risk(

            // Valeur engagée sur le marché / valeur totale.
            BigDecimal exposurePercent,

            String exposureLevel,

            // Plus grosse position / valeur totale.
            String largestPositionSymbol,

            BigDecimal largestPositionPercent,

            String concentrationLevel,

            // 1 - somme des carrés des poids des positions.
            BigDecimal diversificationPercent,

            // Nombre de positions "équivalentes" de même taille (1 / somme des carrés).
            BigDecimal effectivePositions,

            String diversificationLevel,

            // Variation typique sur 5 minutes des actifs détenus, pondérée.
            BigDecimal volatilityPercent,

            String volatilityLevel,

            // Plus forte baisse depuis un sommet, sur l'historique affiché.
            BigDecimal maxDrawdownPercent,

            String drawdownLevel,

            // Le plus défavorable de exposition, concentration,
            // volatilité et baisse maximale.
            String overallLevel
    ) {
    }

    public record EquityPoint(
            Instant time,
            BigDecimal value
    ) {
    }
}
