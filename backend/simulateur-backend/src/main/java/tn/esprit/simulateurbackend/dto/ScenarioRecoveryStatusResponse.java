
package tn.esprit.simulateurbackend.dto;

public record ScenarioRecoveryStatusResponse(
        String symbol,
        String phase,
        double residualIntensity,
        long remainingSeconds
) {}
