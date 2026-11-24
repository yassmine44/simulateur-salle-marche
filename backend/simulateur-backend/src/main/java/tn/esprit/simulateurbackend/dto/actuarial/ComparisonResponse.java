package tn.esprit.simulateurbackend.dto.actuarial;

import java.util.List;

/**
 * Comparaison de deux primes pures : le produit des {@code relativity}
 * vaut exactement {@code premiumRatio}.
 */
public record ComparisonResponse(
        double premiumA,
        double premiumB,
        double premiumRatio,
        List<Contribution> contributions
) {

    public record Contribution(
            String feature,
            String field,
            String label,
            Object valueA,
            Object valueB,
            double phiDifference,
            double relativity
    ) {
    }
}
