package tn.esprit.simulateurbackend.dto.actuarial;

import java.util.List;

/**
 * Devis renvoyé par le moteur actuariel. Les primes sont annuelles.
 *
 * @param frequency         sinistres indemnisés attendus par an
 * @param claimProbability  probabilité d'au moins un sinistre sur {@code years} ans
 * @param severity          coût moyen écrêté d'un sinistre
 * @param largeClaimLoading surcharge de mutualisation des sinistres graves
 * @param purePremium       coût attendu des sinistres (prime pure)
 * @param commercialPremium prime payée par l'assuré
 * @param premiumMargin     prime commerciale − prime pure
 * @param expectedLossRatio prime pure / prime commerciale (S/P attendu)
 * @param explanation       décomposition XAI de la prime pure, ou {@code null}
 */
public record QuoteResponse(
        double years,
        double frequency,
        double claimProbability,
        double severity,
        double largeClaimLoading,
        double purePremium,
        double commercialPremium,
        double premiumMargin,
        double expectedLossRatio,
        PricingAssumptionsDto assumptions,
        ModelVersions modelVersions,
        Explanation explanation
) {

    public record ModelVersions(String frequency, String severity) {
    }

    /** prime pure = basePremium × Π relativity. */
    public record Explanation(
            double basePremium,
            double purePremium,
            List<Contribution> contributions
    ) {
    }

    /**
     * Contribution d'un facteur.
     *
     * @param phi        contribution en échelle logarithmique (valeur de Shapley)
     * @param relativity multiplicateur de prime, exp(phi)
     * @param direction  "hausse", "baisse" ou "neutre"
     */
    public record Contribution(
            String feature,
            String field,
            String label,
            Object value,
            double phi,
            double relativity,
            double frequencyPhi,
            double severityPhi,
            String direction
    ) {
    }
}
