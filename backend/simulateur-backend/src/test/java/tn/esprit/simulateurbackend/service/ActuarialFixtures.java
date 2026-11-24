package tn.esprit.simulateurbackend.service;

import java.util.List;

import tn.esprit.simulateurbackend.dto.actuarial.PolicyProfileRequest;
import tn.esprit.simulateurbackend.dto.actuarial.PricingAssumptionsDto;
import tn.esprit.simulateurbackend.dto.actuarial.QuoteRequest;
import tn.esprit.simulateurbackend.dto.actuarial.QuoteResponse;

/** Jeux de données partagés par les tests du module d'actuariat. */
public final class ActuarialFixtures {

    public static final String QUOTE_JSON = """
            {
              "years": 1.0, "frequency": 0.255324, "claimProbability": 0.225334,
              "severity": 1683.48, "largeClaimLoading": 0.396423,
              "purePremium": 600.23, "commercialPremium": 883.19, "premiumMargin": 282.96,
              "expectedLossRatio": 0.679612,
              "assumptions": {"expenseRatio": 0.25, "profitRatio": 0.05, "safetyLoading": 0.03,
                              "commercialFactor": 1.471429},
              "modelVersions": {"frequency": "20261124162401", "severity": "20261124162402"},
              "currency": {"code": "TND", "label": "dinar tunisien", "rateFromModel": 3.36,
                           "modelCurrency": "EUR", "source": "Cours moyen BCT"},
              "explanation": {
                "basePremium": 135.08, "purePremium": 600.23,
                "contributions": [
                  {"feature": "BonusMalus", "field": "bonusMalus", "label": "Coefficient bonus-malus",
                   "value": 100, "phi": 0.778484, "relativity": 2.178169, "frequencyPhi": 0.778484,
                   "severityPhi": 0.0, "direction": "hausse"},
                  {"feature": "VehBrand", "field": "vehicleBrand", "label": "Marque du véhicule",
                   "value": "B12", "phi": -0.276941, "relativity": 0.758099, "frequencyPhi": -0.276941,
                   "severityPhi": 0.0, "direction": "baisse"}
                ]
              }
            }
            """;

    private ActuarialFixtures() {
    }

    public static PolicyProfileRequest youngDriver() {
        return new PolicyProfileRequest(20, 100, 2, 7, 5000, "E", "B12", "Regular", "R11");
    }

    public static PolicyProfileRequest experiencedDriver() {
        return new PolicyProfileRequest(50, 50, 8, 6, 50, "A", "B1", "Diesel", "R24");
    }

    public static QuoteRequest quoteRequest() {
        return new QuoteRequest(youngDriver(), null, null, null, null);
    }

    public static QuoteResponse quoteResponse() {
        return new QuoteResponse(
                1.0, 0.255324, 0.225334, 1683.48, 0.396423,
                600.23, 883.19, 282.96, 0.679612,
                new PricingAssumptionsDto(0.25, 0.05, 0.03, 1.471429),
                new QuoteResponse.ModelVersions("20261124162401", "20261124162402"),
                new QuoteResponse.Explanation(135.08, 600.23, List.of()),
                new QuoteResponse.Currency("TND", "dinar tunisien", 3.36, "EUR", "Cours moyen BCT")
        );
    }
}
