
package tn.esprit.simulateurbackend.service;

import java.time.Duration;

/**
 * Modèle déterministe de récupération après une crise.
 *
 * Ne modifie ni les ordres, ni les prix, ni PostgreSQL.
 * Produit uniquement une intensité de stress résiduel.
 */
public final class MarketRecoveryModel {

    // Constante de récupération : 30 secondes.
    private static final double TAU_SECONDS = 30.0;

    // Au-delà de 120 secondes, le stress est négligeable.
    public static final Duration RECOVERY_DURATION =
            Duration.ofSeconds(120);

    private MarketRecoveryModel() {
    }

    public static double residualIntensity(
            double initialIntensity,
            Duration elapsed
    ) {

        if (!Double.isFinite(initialIntensity)) {
            throw new IllegalArgumentException(
                    "Intensité invalide"
            );
        }

        if (elapsed == null) {
            throw new IllegalArgumentException(
                    "Durée absente"
            );
        }

        double intensity = Math.max(
                0.0,
                Math.min(1.0, initialIntensity)
        );

        if (elapsed.isNegative() || elapsed.isZero()) {
            return intensity;
        }

        if (elapsed.compareTo(RECOVERY_DURATION) >= 0) {
            return 0.0;
        }

        double seconds =
                elapsed.toMillis() / 1000.0;

        return intensity * Math.exp(
                -seconds / TAU_SECONDS
        );
    }
}
