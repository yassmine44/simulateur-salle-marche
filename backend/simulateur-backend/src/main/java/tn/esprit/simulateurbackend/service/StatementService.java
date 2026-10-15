package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.StatementResponse;
import tn.esprit.simulateurbackend.dto.StatementResponse.StatementPosition;
import tn.esprit.simulateurbackend.dto.StatementResponse.StatementTrade;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.AssetRepository;
import tn.esprit.simulateurbackend.service.PortfolioCalculator.Fill;
import tn.esprit.simulateurbackend.service.PortfolioCalculator.Holding;
import tn.esprit.simulateurbackend.service.PortfolioCalculator.Ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/*
 * Relevé de compte d'une période (un mois par défaut).
 */
@Service
@Transactional(readOnly = true)
public class StatementService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final long MAX_PERIOD_DAYS = 366;

    private final PortfolioService portfolioService;
    private final AssetRepository assetRepository;
    private final HistoricalPrices historicalPrices;

    public StatementService(
            PortfolioService portfolioService,
            AssetRepository assetRepository,
            HistoricalPrices historicalPrices
    ) {
        this.portfolioService = portfolioService;
        this.assetRepository = assetRepository;
        this.historicalPrices = historicalPrices;
    }


    public StatementResponse statement(
            String authenticatedEmail,
            LocalDate requestedFrom,
            LocalDate requestedTo
    ) {

        ZoneId zone = ZoneId.systemDefault();

        LocalDate today = LocalDate.now(zone);

        LocalDate from = requestedFrom != null ? requestedFrom : today.withDayOfMonth(1);

        LocalDate to = requestedTo != null ? requestedTo : today;

        if (to.isAfter(today)) {
            to = today;
        }

        if (from.isAfter(to)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La date de début doit précéder la date de fin."
            );
        }

        if (ChronoUnit.DAYS.between(from, to) > MAX_PERIOD_DAYS) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Un relevé couvre au plus un an."
            );
        }

        User user = portfolioService.findUser(authenticatedEmail);

        PortfolioService.Snapshot snapshot = portfolioService.snapshot(user);

        Instant now = Instant.now();

        Instant start = from.atStartOfDay(zone).toInstant();

        Instant endOfPeriod = to.plusDays(1).atStartOfDay(zone).toInstant();

        // Fin de période = minuit du dernier jour. Pour le mois en cours,
        // c'est dans le futur : toutes les opérations jusqu'à maintenant comptent.
        Instant end = endOfPeriod;

        HistoricalPrices.Lookup prices = historicalPrices.lookup();

        BigDecimal opening = portfolioService.valueAt(snapshot, start, prices);

        BigDecimal closing = portfolioService.valueAt(snapshot, end, prices);

        Ledger atStart =
                PortfolioCalculator.replay(snapshot.initialCapital(), snapshot.fills(), start);

        Ledger atEnd =
                PortfolioCalculator.replay(snapshot.initialCapital(), snapshot.fills(), end);

        // ---- Opérations de la période ----

        List<StatementTrade> trades = new ArrayList<>();

        BigDecimal bought = BigDecimal.ZERO;
        BigDecimal sold = BigDecimal.ZERO;

        for (Fill fill : snapshot.fills()) {

            if (fill.time().isBefore(start) || fill.time().isAfter(end)) {
                continue;
            }

            boolean buy = fill.signedQuantity().signum() > 0;

            BigDecimal quantity = fill.signedQuantity().abs();

            BigDecimal amount = quantity.multiply(fill.price());

            if (buy) {
                bought = bought.add(amount);
            } else {
                sold = sold.add(amount);
            }

            trades.add(
                    new StatementTrade(
                            fill.tradeId(),
                            fill.time(),
                            fill.symbol(),
                            buy ? OrderSide.BUY : OrderSide.SELL,
                            plain(quantity),
                            plain(fill.price()),
                            money(amount)
                    )
            );
        }

        // ---- Positions en fin de période ----

        List<StatementPosition> positions = new ArrayList<>();

        for (Holding holding : atEnd.holdings()) {

            if (!holding.isOpen()) {
                continue;
            }

            BigDecimal price = prices.priceAt(holding.symbol(), end, holding.averagePrice());

            positions.add(
                    new StatementPosition(
                            holding.symbol(),
                            assetRepository
                                    .findBySymbolIgnoreCase(holding.symbol())
                                    .map(asset -> asset.getName())
                                    .orElse(holding.symbol()),
                            plain(holding.quantity()),
                            plain(holding.averagePrice()),
                            plain(price),
                            money(holding.quantity().multiply(price))
                    )
            );
        }

        BigDecimal netChange = closing.subtract(opening);

        return new StatementResponse(
                statementNumber(user, from, to),
                "SIM-" + String.format("%06d", user.getId()),
                (user.getFirstName() + " " + user.getLastName()).trim(),
                user.getEmail(),
                portfolioService.currency(),
                from,
                to,
                now,
                snapshot.startedAt(),
                money(opening),
                money(closing),
                money(netChange),
                opening.signum() == 0
                        ? BigDecimal.ZERO.setScale(2)
                        : netChange.multiply(HUNDRED).divide(opening, 2, RoundingMode.HALF_UP),
                money(atEnd.totalRealizedPnl().subtract(atStart.totalRealizedPnl())),
                money(atEnd.cash()),
                trades.size(),
                money(bought),
                money(sold),
                trades,
                positions
        );
    }


    private String statementNumber(
            User user,
            LocalDate from,
            LocalDate to
    ) {

        DateTimeFormatter format = DateTimeFormatter.BASIC_ISO_DATE;

        return "REL-" + user.getId() + "-" + from.format(format) + "-" + to.format(format);
    }


    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }


    private static BigDecimal plain(BigDecimal value) {

        BigDecimal stripped = value.stripTrailingZeros();

        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
