package tn.esprit.simulateurbackend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Comptabilité du portefeuille, sans dépendance à Spring
 * ni à la base de données : testable en isolation.
 *
 * Rejoue les transactions d'un utilisateur dans l'ordre
 * chronologique selon la méthode du prix de revient moyen
 * pondéré (PRMP) :
 *
 * - un achat augmente la position et recalcule le prix moyen ;
 * - une vente réduit la position au prix moyen inchangé et
 *   dégage un résultat réalisé = (prix de vente - prix moyen) x quantité ;
 * - une transaction qui dépasse la position la retourne : le
 *   reliquat ouvre une position inverse au prix de la transaction.
 *
 * Chaque position ouverte puis ramenée à zéro forme un
 * « aller-retour » : c'est l'unité des statistiques de trader.
 */
public final class PortfolioCalculator {

    private PortfolioCalculator() {
    }


    /*
     * Une transaction vue du côté de l'utilisateur :
     * quantité positive pour un achat, négative pour une vente.
     */
    public record Fill(
            Instant time,
            long tradeId,
            String symbol,
            BigDecimal signedQuantity,
            BigDecimal price
    ) {
    }


    /*
     * Position ouverte puis entièrement fermée.
     */
    public record RoundTrip(
            String symbol,
            // LONG : achat puis vente ; SHORT : vente puis rachat.
            String direction,
            Instant openedAt,
            Instant closedAt,
            // Plus grande quantité détenue pendant l'aller-retour.
            BigDecimal maxQuantity,
            // Montant total engagé à l'ouverture et aux renforcements.
            BigDecimal invested,
            BigDecimal realizedPnl
    ) {

        public BigDecimal returnPercent() {

            if (invested.signum() == 0) {
                return BigDecimal.ZERO;
            }

            return realizedPnl
                    .multiply(BigDecimal.valueOf(100))
                    .divide(invested, 2, RoundingMode.HALF_UP);
        }

        public boolean isWin() {
            return realizedPnl.signum() > 0;
        }
    }


    public static final class Holding {

        private final String symbol;

        private BigDecimal quantity = BigDecimal.ZERO;

        private BigDecimal averagePrice = BigDecimal.ZERO;

        private BigDecimal realizedPnl = BigDecimal.ZERO;

        private BigDecimal boughtQuantity = BigDecimal.ZERO;

        private BigDecimal soldQuantity = BigDecimal.ZERO;

        private Instant openedAt;

        // Aller-retour en cours.
        private BigDecimal cycleRealized = BigDecimal.ZERO;

        private BigDecimal cycleInvested = BigDecimal.ZERO;

        private BigDecimal cycleMaxQuantity = BigDecimal.ZERO;

        private Holding(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        public BigDecimal quantity() {
            return quantity;
        }

        public BigDecimal averagePrice() {
            return averagePrice;
        }

        public BigDecimal realizedPnl() {
            return realizedPnl;
        }

        public BigDecimal boughtQuantity() {
            return boughtQuantity;
        }

        public BigDecimal soldQuantity() {
            return soldQuantity;
        }

        // Début de la position actuellement ouverte.
        public Instant openedAt() {
            return openedAt;
        }

        public boolean isOpen() {
            return quantity.signum() != 0;
        }
    }


    public static final class Ledger {

        private BigDecimal cash;

        private final Map<String, Holding> holdings =
                new LinkedHashMap<>();

        private final List<RoundTrip> roundTrips =
                new ArrayList<>();

        private Ledger(BigDecimal cash) {
            this.cash = cash;
        }

        // Capital - achats + ventes.
        public BigDecimal cash() {
            return cash;
        }

        public Collection<Holding> holdings() {
            return holdings.values();
        }

        public Holding holding(String symbol) {
            return holdings.get(symbol);
        }

        public BigDecimal quantity(String symbol) {

            Holding holding = holdings.get(symbol);

            return holding == null
                    ? BigDecimal.ZERO
                    : holding.quantity;
        }

        // Allers-retours terminés, dans l'ordre chronologique.
        public List<RoundTrip> roundTrips() {
            return Collections.unmodifiableList(roundTrips);
        }

        public BigDecimal totalRealizedPnl() {

            return holdings.values().stream()
                    .map(Holding::realizedPnl)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }


    /*
     * fills doit être trié par date puis identifiant.
     * until null = toutes les transactions.
     */
    public static Ledger replay(
            BigDecimal initialCapital,
            List<Fill> fills,
            Instant until
    ) {

        Ledger ledger = new Ledger(initialCapital);

        for (Fill fill : fills) {

            if (
                    until != null
                            && fill.time().isAfter(until)
            ) {
                break;
            }

            // Un achat consomme des liquidités, une vente en rapporte.
            ledger.cash =
                    ledger.cash.subtract(
                            fill.signedQuantity().multiply(fill.price())
                    );

            apply(
                    ledger,
                    ledger.holdings.computeIfAbsent(
                            fill.symbol(),
                            Holding::new
                    ),
                    fill
            );
        }

        return ledger;
    }


    private static void apply(
            Ledger ledger,
            Holding holding,
            Fill fill
    ) {

        BigDecimal quantity = fill.signedQuantity();

        if (quantity.signum() > 0) {
            holding.boughtQuantity = holding.boughtQuantity.add(quantity);
        } else {
            holding.soldQuantity = holding.soldQuantity.add(quantity.abs());
        }

        boolean increasesPosition =
                holding.quantity.signum() == 0
                        || holding.quantity.signum() == quantity.signum();

        if (increasesPosition) {

            if (holding.quantity.signum() == 0) {
                startCycle(holding, fill.time());
            }

            open(holding, quantity, fill.price());

            return;
        }

        // Réduction ou retournement de la position.
        BigDecimal closedQuantity =
                quantity.abs().min(holding.quantity.abs());

        int previousSign = holding.quantity.signum();

        BigDecimal realized =
                fill.price()
                        .subtract(holding.averagePrice)
                        .multiply(closedQuantity)
                        .multiply(BigDecimal.valueOf(previousSign));

        holding.realizedPnl = holding.realizedPnl.add(realized);
        holding.cycleRealized = holding.cycleRealized.add(realized);

        holding.quantity = holding.quantity.add(quantity);

        if (holding.quantity.signum() == previousSign) {
            return;
        }

        // Position ramenée à zéro (ou retournée) : l'aller-retour est terminé.
        ledger.roundTrips.add(
                new RoundTrip(
                        holding.symbol,
                        previousSign > 0 ? "LONG" : "SHORT",
                        holding.openedAt,
                        fill.time(),
                        holding.cycleMaxQuantity,
                        holding.cycleInvested,
                        holding.cycleRealized
                )
        );

        if (holding.quantity.signum() == 0) {

            holding.averagePrice = BigDecimal.ZERO;
            holding.openedAt = null;

            return;
        }

        // Retournement : le reliquat ouvre une position inverse.
        BigDecimal remainder = holding.quantity;

        holding.quantity = BigDecimal.ZERO;
        holding.averagePrice = BigDecimal.ZERO;

        startCycle(holding, fill.time());

        open(holding, remainder, fill.price());
    }


    private static void startCycle(
            Holding holding,
            Instant time
    ) {

        holding.openedAt = time;
        holding.cycleRealized = BigDecimal.ZERO;
        holding.cycleInvested = BigDecimal.ZERO;
        holding.cycleMaxQuantity = BigDecimal.ZERO;
    }


    // Augmente la position et recalcule le prix moyen pondéré.
    private static void open(
            Holding holding,
            BigDecimal quantity,
            BigDecimal price
    ) {

        BigDecimal added = quantity.abs().multiply(price);

        BigDecimal totalCost =
                holding.quantity.abs()
                        .multiply(holding.averagePrice)
                        .add(added);

        holding.quantity = holding.quantity.add(quantity);

        holding.averagePrice =
                totalCost.divide(
                        holding.quantity.abs(),
                        8,
                        RoundingMode.HALF_UP
                );

        holding.cycleInvested = holding.cycleInvested.add(added);

        holding.cycleMaxQuantity =
                holding.cycleMaxQuantity.max(holding.quantity.abs());
    }
}
