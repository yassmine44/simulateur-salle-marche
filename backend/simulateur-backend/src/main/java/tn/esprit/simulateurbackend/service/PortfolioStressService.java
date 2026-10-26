
package tn.esprit.simulateurbackend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.simulateurbackend.dto.PortfolioResponse;
import tn.esprit.simulateurbackend.dto.PortfolioStressResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PortfolioStressService {

    private final PortfolioService portfolioService;
    private final MarketMakerService marketMakerService;

    public PortfolioStressService(
            PortfolioService portfolioService,
            MarketMakerService marketMakerService
    ) {
        this.portfolioService = portfolioService;
        this.marketMakerService = marketMakerService;
    }

    public PortfolioStressResponse getStress(String email) {

        PortfolioResponse portfolio =
                portfolioService.getPortfolio(email, 7);

        List<PortfolioStressResponse.PositionImpact> positions =
                new ArrayList<>();

        BigDecimal totalImpact = BigDecimal.ZERO;

        boolean coverageComplete = true;

        for (PortfolioResponse.Position position
                : portfolio.openPositions()) {

            String symbol = position.assetSymbol();

            BigDecimal quotedMidPrice =
                    marketMakerService.currentQuotedMidPrice(symbol);

            BigDecimal normalPrice = position.lastPrice();

            if (quotedMidPrice == null
                    || quotedMidPrice.signum() <= 0
                    || normalPrice == null
                    || normalPrice.signum() <= 0) {

                coverageComplete = false;

                positions.add(
                        new PortfolioStressResponse.PositionImpact(
                                symbol,
                                position.quantity(),
                                normalPrice,
                                quotedMidPrice,
                                position.marketValue(),
                                null,
                                null
                        )
                );

                continue;
            }

            PortfolioStressCalculator.Result result =
                    PortfolioStressCalculator.calculate(
                            symbol,
                            position.quantity(),
                            normalPrice,
                            quotedMidPrice,
                            portfolio.totalValue()
                    );

            totalImpact =
                    totalImpact.add(result.pnlImpact());

            positions.add(
                    new PortfolioStressResponse.PositionImpact(
                            symbol,
                            position.quantity(),
                            normalPrice,
                            quotedMidPrice,
                            result.normalPositionValue(),
                            result.stressedPositionValue(),
                            result.pnlImpact()
                    )
            );
        }

        BigDecimal quotedTotalValue = null;
        BigDecimal valuationDifference = null;
        BigDecimal valuationDifferencePercent = null;

        if (coverageComplete) {

            valuationDifference =
                    totalImpact.setScale(
                            2,
                            RoundingMode.HALF_UP
                    );

            quotedTotalValue =
                    portfolio.totalValue()
                            .add(valuationDifference);

            if (portfolio.totalValue().signum() > 0) {

                valuationDifferencePercent =
                        valuationDifference
                                .multiply(BigDecimal.valueOf(100))
                                .divide(
                                        portfolio.totalValue(),
                                        2,
                                        RoundingMode.HALF_UP
                                );
            }
        }

        return new PortfolioStressResponse(
                portfolio.currency(),
                portfolio.totalValue(),
                quotedTotalValue,
                valuationDifference,
                valuationDifferencePercent,
                coverageComplete,
                List.copyOf(positions),
                Instant.now()
        );
    }
}
