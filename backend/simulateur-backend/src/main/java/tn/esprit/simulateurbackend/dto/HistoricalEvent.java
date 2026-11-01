package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.ImpactLevel;

public record HistoricalEvent(
        String date,
        String description,
        ImpactLevel impact
) {
}
