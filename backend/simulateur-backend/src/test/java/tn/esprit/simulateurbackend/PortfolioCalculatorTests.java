package tn.esprit.simulateurbackend;

import org.junit.jupiter.api.Test;

import tn.esprit.simulateurbackend.service.PortfolioCalculator;
import tn.esprit.simulateurbackend.service.PortfolioCalculator.Fill;
import tn.esprit.simulateurbackend.service.PortfolioCalculator.Holding;
import tn.esprit.simulateurbackend.service.PortfolioCalculator.Ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Comptabilité du portefeuille (prix de revient moyen pondéré),
 * sans base de données ni contexte Spring.
 */
class PortfolioCalculatorTests {

    private static final BigDecimal CAPITAL = new BigDecimal("100000");

    private final List<Fill> fills = new ArrayList<>();

    private Instant clock = Instant.parse("2026-10-01T09:00:00Z");


    @Test
    void noTradeLeavesTheCapitalUntouched() {

        Ledger ledger = replay();

        assertThat(ledger.cash()).isEqualByComparingTo("100000");
        assertThat(ledger.holdings()).isEmpty();
    }


    @Test
    void purchasesAreAveragedByQuantity() {

        buy("AAPL", "10", "100");
        buy("AAPL", "30", "120");

        Holding holding = replay().holding("AAPL");

        assertThat(holding.quantity()).isEqualByComparingTo("40");

        // (10 x 100 + 30 x 120) / 40
        assertThat(holding.averagePrice()).isEqualByComparingTo("115");

        assertThat(holding.realizedPnl()).isEqualByComparingTo("0");
    }


    @Test
    void partialSaleRealizesAtTheAverageCost() {

        buy("AAPL", "10", "100");
        sell("AAPL", "4", "110");

        Ledger ledger = replay();

        Holding holding = ledger.holding("AAPL");

        assertThat(holding.quantity()).isEqualByComparingTo("6");

        // Le prix moyen du reste ne change pas.
        assertThat(holding.averagePrice()).isEqualByComparingTo("100");

        // 4 x (110 - 100)
        assertThat(holding.realizedPnl()).isEqualByComparingTo("40");

        // 100000 - 1000 + 440
        assertThat(ledger.cash()).isEqualByComparingTo("99440");
    }


    @Test
    void fullSaleClosesThePositionWithItsResult() {

        buy("AAPL", "10", "100");
        sell("AAPL", "10", "90");

        Holding holding = replay().holding("AAPL");

        assertThat(holding.isOpen()).isFalse();
        assertThat(holding.averagePrice()).isEqualByComparingTo("0");
        assertThat(holding.openedAt()).isNull();

        // 10 x (90 - 100)
        assertThat(holding.realizedPnl()).isEqualByComparingTo("-100");

        assertThat(holding.boughtQuantity()).isEqualByComparingTo("10");
        assertThat(holding.soldQuantity()).isEqualByComparingTo("10");
    }


    @Test
    void reopeningAPositionStartsANewAverage() {

        buy("AAPL", "10", "100");
        sell("AAPL", "10", "110");
        buy("AAPL", "5", "200");

        Holding holding = replay().holding("AAPL");

        assertThat(holding.quantity()).isEqualByComparingTo("5");
        assertThat(holding.averagePrice()).isEqualByComparingTo("200");

        // Le résultat de la première position est conservé.
        assertThat(holding.realizedPnl()).isEqualByComparingTo("100");
    }


    @Test
    void sellingMoreThanHeldFlipsThePosition() {

        // Données antérieures au contrôle des ordres :
        // une vente pouvait dépasser la quantité détenue.
        buy("AAPL", "10", "100");
        sell("AAPL", "15", "90");

        Holding holding = replay().holding("AAPL");

        assertThat(holding.quantity()).isEqualByComparingTo("-5");
        assertThat(holding.averagePrice()).isEqualByComparingTo("90");
        assertThat(holding.realizedPnl()).isEqualByComparingTo("-100");
    }


    @Test
    void assetsAreTrackedIndependently() {

        buy("AAPL", "10", "100");
        buy("BTCUSD", "0.5", "60000");
        sell("AAPL", "10", "120");

        Ledger ledger = replay();

        assertThat(ledger.holding("AAPL").isOpen()).isFalse();

        assertThat(ledger.holding("BTCUSD").quantity())
                .isEqualByComparingTo("0.5");

        // 100000 - 1000 - 30000 + 1200
        assertThat(ledger.cash()).isEqualByComparingTo("70200");
    }


    @Test
    void replayCanStopAtADate() {

        buy("AAPL", "10", "100");

        Instant afterFirstTrade = clock;

        buy("AAPL", "10", "200");

        Ledger past =
                PortfolioCalculator.replay(CAPITAL, fills, afterFirstTrade);

        assertThat(past.quantity("AAPL")).isEqualByComparingTo("10");
        assertThat(past.cash()).isEqualByComparingTo("99000");

        assertThat(replay().quantity("AAPL")).isEqualByComparingTo("20");
    }


    // =========================================
    // ROUND TRIPS
    // =========================================

    @Test
    void closingAPositionRecordsARoundTrip() {

        buy("AAPL", "10", "100");
        buy("AAPL", "10", "120");
        sell("AAPL", "5", "130");

        // Position encore ouverte : pas d'aller-retour.
        assertThat(replay().roundTrips()).isEmpty();

        sell("AAPL", "15", "100");

        List<PortfolioCalculator.RoundTrip> trips = replay().roundTrips();

        assertThat(trips).hasSize(1);

        PortfolioCalculator.RoundTrip trip = trips.get(0);

        assertThat(trip.direction()).isEqualTo("LONG");
        assertThat(trip.maxQuantity()).isEqualByComparingTo("20");

        // 10 x 100 + 10 x 120
        assertThat(trip.invested()).isEqualByComparingTo("2200");

        // Prix moyen 110 : 5 x (130 - 110) + 15 x (100 - 110) = 100 - 150
        assertThat(trip.realizedPnl()).isEqualByComparingTo("-50");
        assertThat(trip.isWin()).isFalse();

        assertThat(trip.returnPercent()).isEqualByComparingTo("-2.27");
        assertThat(trip.closedAt()).isAfter(trip.openedAt());
    }


    @Test
    void flippingAPositionClosesOneRoundTripAndOpensAnother() {

        buy("AAPL", "10", "100");
        sell("AAPL", "15", "110");

        Ledger ledger = replay();

        assertThat(ledger.roundTrips()).hasSize(1);
        assertThat(ledger.roundTrips().get(0).realizedPnl()).isEqualByComparingTo("100");

        Holding holding = ledger.holding("AAPL");

        assertThat(holding.quantity()).isEqualByComparingTo("-5");
        assertThat(holding.averagePrice()).isEqualByComparingTo("110");
        assertThat(holding.openedAt()).isNotNull();
    }


    // =========================================
    // HELPERS
    // =========================================

    private void buy(String symbol, String quantity, String price) {
        add(symbol, new BigDecimal(quantity), price);
    }


    private void sell(String symbol, String quantity, String price) {
        add(symbol, new BigDecimal(quantity).negate(), price);
    }


    private void add(String symbol, BigDecimal quantity, String price) {

        clock = clock.plusSeconds(60);

        fills.add(
                new Fill(
                        clock,
                        fills.size() + 1,
                        symbol,
                        quantity,
                        new BigDecimal(price)
                )
        );
    }


    private Ledger replay() {

        return PortfolioCalculator.replay(CAPITAL, fills, null);
    }
}
