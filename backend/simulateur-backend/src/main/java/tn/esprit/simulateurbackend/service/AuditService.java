package tn.esprit.simulateurbackend.service;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Service;

import tn.esprit.simulateurbackend.entity.AuditEventType;
import tn.esprit.simulateurbackend.entity.SecurityAuditLog;
import tn.esprit.simulateurbackend.repository.SecurityAuditLogRepository;


@Service
public class AuditService {

    private final SecurityAuditLogRepository repository;


    public AuditService(
            SecurityAuditLogRepository repository
    ) {

        this.repository =
                repository;
    }


    public void log(
            AuditEventType eventType,
            Long userId,
            String email,
            String actorEmail,
            boolean success,
            String details,
            HttpServletRequest request
    ) {

        SecurityAuditLog log =
                new SecurityAuditLog();


        log.setEventType(
                eventType
        );

        log.setUserId(
                userId
        );

        log.setEmail(
                normalize(email)
        );

        log.setActorEmail(
                normalize(actorEmail)
        );

        log.setSuccess(
                success
        );

        log.setDetails(
                truncate(
                        details,
                        1000
                )
        );


        if (request != null) {

            log.setIpAddress(
                    truncate(
                            request.getRemoteAddr(),
                            64
                    )
            );


            log.setUserAgent(
                    truncate(
                            request.getHeader(
                                    "User-Agent"
                            ),
                            500
                    )
            );


            log.setEndpoint(
                    truncate(
                            request.getRequestURI(),
                            255
                    )
            );
        }


        repository.save(
                log
        );
    }


    private String normalize(
            String value
    ) {

        if (
                value == null ||
                        value.isBlank()
        ) {
            return null;
        }

        return value
                .trim()
                .toLowerCase();
    }


    private String truncate(
            String value,
            int maxLength
    ) {

        if (value == null) {
            return null;
        }

        if (
                value.length()
                        <= maxLength
        ) {
            return value;
        }

        return value.substring(
                0,
                maxLength
        );
    }
}