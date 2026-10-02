package tn.esprit.simulateurbackend.dto;

import java.time.LocalDateTime;

import tn.esprit.simulateurbackend.entity.AuditEventType;
import tn.esprit.simulateurbackend.entity.SecurityAuditLog;


public record LoginAuditResponse(

        Long id,

        Long userId,

        String email,

        LoginMethod method,

        String ipAddress,

        String userAgent,

        boolean success,

        String details,

        LocalDateTime createdAt

) {

    public static LoginAuditResponse from(
            SecurityAuditLog log
    ) {

        return new LoginAuditResponse(

                log.getId(),

                log.getUserId(),

                log.getEmail(),

                resolveMethod(
                        log.getEventType()
                ),

                log.getIpAddress(),

                log.getUserAgent(),

                log.isSuccess(),

                log.getDetails(),

                log.getCreatedAt()
        );
    }


    private static LoginMethod resolveMethod(
            AuditEventType eventType
    ) {

        if (
                eventType == AuditEventType.GOOGLE_LOGIN_SUCCESS
                        ||
                        eventType == AuditEventType.GOOGLE_LOGIN_FAILED
        ) {

            return LoginMethod.GOOGLE;
        }


        return LoginMethod.LOCAL;
    }
}