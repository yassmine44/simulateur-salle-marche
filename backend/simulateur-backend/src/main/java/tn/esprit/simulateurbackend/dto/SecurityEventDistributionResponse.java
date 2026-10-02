package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.AuditEventType;

public record SecurityEventDistributionResponse(

        AuditEventType eventType,

        long count,

        double percentage

) {
}