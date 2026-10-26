package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.ScenarioType;

import java.math.BigDecimal;
import java.time.Instant;

public record ScenarioStatusResponse(

        boolean active,
        ScenarioType type,
        String symbol,
        BigDecimal intensity,
        Instant startedAt,
        Instant endsAt,
        long remainingSeconds

) {}