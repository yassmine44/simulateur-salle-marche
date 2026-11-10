package tn.esprit.simulateurbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import tn.esprit.simulateurbackend.banking.BalanceSheetSide;
import tn.esprit.simulateurbackend.banking.BankingModel;
import tn.esprit.simulateurbackend.banking.BankingStressScenarioType;
import tn.esprit.simulateurbackend.banking.FinancialContractType;
import tn.esprit.simulateurbackend.banking.ReturnBasis;

import java.math.BigDecimal;
import java.util.List;

public record BankingStressTestRequest(

        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "La devise doit être un code ISO à trois lettres majuscules.")
        String currency,

        @NotNull
        @Min(1)
        @Max(3650)
        Integer creditHorizonDays,

        @NotEmpty
        @Size(max = 100)
        List<@NotNull @Valid BankInput> banks,

        @NotEmpty
        @Size(max = 100)
        List<@NotNull @Valid ScenarioInput> scenarios
) {

    public record BankInput(

            @NotBlank
            @Size(max = 80)
            String bankId,

            @NotBlank
            @Size(max = 120)
            String bankName,

            @NotNull
            BankingModel model,

            @NotNull
            @DecimalMin("0")
            @Digits(integer = 15, fraction = 8)
            BigDecimal capitalProxy,

            @NotNull
            @DecimalMin("0")
            @Digits(integer = 15, fraction = 8)
            BigDecimal riskWeightedAssets,

            @NotNull
            @Size(max = 500)
            List<@NotNull @Valid CreditExposure> creditExposures,

            @NotNull
            @Size(max = 500)
            List<@NotNull @Valid RateExposure> rateExposures,

            @NotNull
            @Size(max = 500)
            List<@NotNull @Valid MarketExposure> marketExposures
    ) {
    }

    public record ExposureTerms(

            @NotNull
            FinancialContractType contractType,

            @NotNull
            ReturnBasis returnBasis,

            @NotNull
            @DecimalMin("0")
            @DecimalMax("1")
            @Digits(integer = 1, fraction = 8)
            BigDecimal bankLossShare,

            @NotNull
            @DecimalMin("0")
            @DecimalMax("1")
            @Digits(integer = 1, fraction = 8)
            BigDecimal investorLossShare
    ) {
    }

    public record CreditExposure(

            @NotBlank
            @Size(max = 80)
            String exposureId,

            @NotNull
            @Valid
            ExposureTerms terms,

            @NotNull
            @DecimalMin("0")
            @Digits(integer = 15, fraction = 8)
            BigDecimal exposureAtDefault,

            @NotNull
            @DecimalMin("0")
            @DecimalMax("1")
            @Digits(integer = 1, fraction = 8)
            BigDecimal probabilityOfDefault,

            @NotNull
            @DecimalMin("0")
            @DecimalMax("1")
            @Digits(integer = 1, fraction = 8)
            BigDecimal lossGivenDefault
    ) {
    }

    public record RateExposure(

            @NotBlank
            @Size(max = 80)
            String exposureId,

            @NotNull
            @Valid
            ExposureTerms terms,

            @NotNull
            BalanceSheetSide side,

            @NotNull
            @DecimalMin("0")
            @Digits(integer = 15, fraction = 8)
            BigDecimal presentValue,

            @NotNull
            @DecimalMin("0")
            @Digits(integer = 8, fraction = 8)
            BigDecimal modifiedDurationYears,

            @NotNull
            @DecimalMin("0")
            @Digits(integer = 8, fraction = 8)
            BigDecimal convexityYearsSquared
    ) {
    }

    public record MarketExposure(

            @NotBlank
            @Size(max = 80)
            String exposureId,

            @NotNull
            @Valid
            ExposureTerms terms,

            @NotNull
            @DecimalMin("0")
            @Digits(integer = 15, fraction = 8)
            BigDecimal marketValue
    ) {
    }

    public record ScenarioInput(

            @NotBlank
            @Size(max = 80)
            String scenarioId,

            @NotNull
            BankingStressScenarioType type,

            // Fractions: 0.01 = 1%; rate shocks are absolute annual yield changes.
            @DecimalMin(value = "-1")
            @DecimalMax(value = "1")
            @Digits(integer = 1, fraction = 8)
            BigDecimal rateShock,

            @DecimalMin(value = "1")
            @DecimalMax(value = "100")
            @Digits(integer = 3, fraction = 8)
            BigDecimal recessionProbabilityMultiplier,

            @DecimalMin("0")
            @DecimalMax("1")
            @Digits(integer = 1, fraction = 8)
            BigDecimal recessionLossGivenDefaultAddOn,

            @DecimalMin("0")
            @DecimalMax("1")
            @Digits(integer = 1, fraction = 8)
            BigDecimal defaultProbabilityAddOn,

            // A return of -1 represents a total loss of market value.
            @DecimalMin(value = "-1")
            @DecimalMax(value = "1")
            @Digits(integer = 1, fraction = 8)
            BigDecimal equityReturnShock
    ) {
    }
}
