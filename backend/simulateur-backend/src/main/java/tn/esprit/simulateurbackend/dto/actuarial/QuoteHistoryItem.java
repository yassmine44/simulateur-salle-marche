package tn.esprit.simulateurbackend.dto.actuarial;

import java.math.BigDecimal;
import java.time.Instant;

import tn.esprit.simulateurbackend.entity.ActuarialQuote;

/** Ligne de l'historique des devis d'un utilisateur. */
public record QuoteHistoryItem(
        Long id,
        Instant createdAt,
        int driverAge,
        int bonusMalus,
        String area,
        String vehicleBrand,
        double claimProbability,
        BigDecimal purePremium,
        BigDecimal commercialPremium,
        BigDecimal premiumMargin,
        String frequencyModelVersion
) {

    public static QuoteHistoryItem from(ActuarialQuote quote) {
        return new QuoteHistoryItem(
                quote.getId(),
                quote.getCreatedAt(),
                quote.getDriverAge(),
                quote.getBonusMalus(),
                quote.getArea(),
                quote.getVehicleBrand(),
                quote.getClaimProbability(),
                quote.getPurePremium(),
                quote.getCommercialPremium(),
                quote.getPremiumMargin(),
                quote.getFrequencyModelVersion()
        );
    }
}
