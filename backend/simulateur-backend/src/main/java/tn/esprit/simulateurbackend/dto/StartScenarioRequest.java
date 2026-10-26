package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tn.esprit.simulateurbackend.entity.ScenarioType;

import java.math.BigDecimal;

public record StartScenarioRequest(

        @NotNull
        ScenarioType type,

        @NotBlank
        String symbol,

        @NotNull
        @Min(30)
        @Max(300)
        Integer durationSeconds,

        @NotNull
        @DecimalMin("0.1")
        @DecimalMax("1.0")
        BigDecimal intensity

) {}