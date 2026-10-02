package tn.esprit.simulateurbackend.dto;

import java.time.LocalDateTime;

import tn.esprit.simulateurbackend.entity.AuditEventType;
import tn.esprit.simulateurbackend.entity.SecurityAuditLog;


public record SecurityAuditLogResponse(

        Long id,

        AuditEventType eventType,

        Long userId,

        String email,

        String actorEmail,

        String ipAddress,

        String userAgent,

        String endpoint,

        boolean success,

        String details,

        LocalDateTime createdAt

) {

    public static SecurityAuditLogResponse from(
            SecurityAuditLog log
    ) {

        return new SecurityAuditLogResponse(

                log.getId(),

                log.getEventType(),

                log.getUserId(),

                log.getEmail(),

                log.getActorEmail(),

                log.getIpAddress(),

                log.getUserAgent(),

                log.getEndpoint(),

                log.isSuccess(),

                log.getDetails(),

                log.getCreatedAt()
        );
    }
}