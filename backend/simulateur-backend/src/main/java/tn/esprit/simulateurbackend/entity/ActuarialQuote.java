package tn.esprit.simulateurbackend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import tn.esprit.simulateurbackend.dto.actuarial.PolicyProfileRequest;
import tn.esprit.simulateurbackend.dto.actuarial.QuoteResponse;

/**
 * Trace d'un devis actuariel (cahier des charges §4 : traçabilité).
 *
 * <p>Conserve les entrées, les résultats, les hypothèses commerciales et les
 * versions des modèles. Les modèles étant versionnés et déterministes, le
 * devis et son explication peuvent être reproduits à l'identique.
 */
@Entity
@Table(
        name = "actuarial_quotes",
        indexes = @Index(name = "idx_actuarial_quote_user_created", columnList = "user_email, created_at")
)
public class ActuarialQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // --- Profil -----------------------------------------------------------
    @Column(name = "driver_age", nullable = false)
    private int driverAge;

    @Column(name = "bonus_malus", nullable = false)
    private int bonusMalus;

    @Column(name = "vehicle_age", nullable = false)
    private int vehicleAge;

    @Column(name = "vehicle_power", nullable = false)
    private int vehiclePower;

    @Column(nullable = false)
    private int density;

    @Column(nullable = false, length = 1)
    private String area;

    @Column(name = "vehicle_brand", nullable = false, length = 4)
    private String vehicleBrand;

    @Column(name = "vehicle_gas", nullable = false, length = 10)
    private String vehicleGas;

    @Column(nullable = false, length = 4)
    private String region;

    // --- Résultats ---------------------------------------------------------
    @Column(nullable = false)
    private double years;

    @Column(nullable = false)
    private double frequency;

    @Column(name = "claim_probability", nullable = false)
    private double claimProbability;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal severity;

    @Column(name = "pure_premium", nullable = false, precision = 14, scale = 2)
    private BigDecimal purePremium;

    @Column(name = "commercial_premium", nullable = false, precision = 14, scale = 2)
    private BigDecimal commercialPremium;

    @Column(name = "premium_margin", nullable = false, precision = 14, scale = 2)
    private BigDecimal premiumMargin;

    // --- Hypothèses et versions -------------------------------------------
    @Column(name = "expense_ratio", nullable = false)
    private double expenseRatio;

    @Column(name = "profit_ratio", nullable = false)
    private double profitRatio;

    @Column(name = "safety_loading", nullable = false)
    private double safetyLoading;

    @Column(name = "frequency_model_version", nullable = false, length = 32)
    private String frequencyModelVersion;

    @Column(name = "severity_model_version", nullable = false, length = 32)
    private String severityModelVersion;

    protected ActuarialQuote() {
        // Constructeur requis par JPA.
    }

    public static ActuarialQuote record(
            String userEmail,
            PolicyProfileRequest profile,
            QuoteResponse quote,
            Instant createdAt
    ) {
        ActuarialQuote entity = new ActuarialQuote();
        entity.userEmail = userEmail;
        entity.createdAt = createdAt;

        entity.driverAge = profile.driverAge();
        entity.bonusMalus = profile.bonusMalus();
        entity.vehicleAge = profile.vehicleAge();
        entity.vehiclePower = profile.vehiclePower();
        entity.density = profile.density();
        entity.area = profile.area();
        entity.vehicleBrand = profile.vehicleBrand();
        entity.vehicleGas = profile.vehicleGas();
        entity.region = profile.region();

        entity.years = quote.years();
        entity.frequency = quote.frequency();
        entity.claimProbability = quote.claimProbability();
        entity.severity = money(quote.severity());
        entity.purePremium = money(quote.purePremium());
        entity.commercialPremium = money(quote.commercialPremium());
        entity.premiumMargin = money(quote.premiumMargin());

        entity.expenseRatio = quote.assumptions().expenseRatio();
        entity.profitRatio = quote.assumptions().profitRatio();
        entity.safetyLoading = quote.assumptions().safetyLoading();
        entity.frequencyModelVersion = quote.modelVersions().frequency();
        entity.severityModelVersion = quote.modelVersions().severity();
        return entity;
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public String getUserEmail() { return userEmail; }
    public Instant getCreatedAt() { return createdAt; }
    public int getDriverAge() { return driverAge; }
    public int getBonusMalus() { return bonusMalus; }
    public int getVehicleAge() { return vehicleAge; }
    public int getVehiclePower() { return vehiclePower; }
    public int getDensity() { return density; }
    public String getArea() { return area; }
    public String getVehicleBrand() { return vehicleBrand; }
    public String getVehicleGas() { return vehicleGas; }
    public String getRegion() { return region; }
    public double getYears() { return years; }
    public double getFrequency() { return frequency; }
    public double getClaimProbability() { return claimProbability; }
    public BigDecimal getSeverity() { return severity; }
    public BigDecimal getPurePremium() { return purePremium; }
    public BigDecimal getCommercialPremium() { return commercialPremium; }
    public BigDecimal getPremiumMargin() { return premiumMargin; }
    public double getExpenseRatio() { return expenseRatio; }
    public double getProfitRatio() { return profitRatio; }
    public double getSafetyLoading() { return safetyLoading; }
    public String getFrequencyModelVersion() { return frequencyModelVersion; }
    public String getSeverityModelVersion() { return severityModelVersion; }
}
