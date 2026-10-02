package tn.esprit.simulateurbackend.security;

import io.github.bucket4j.Bucket;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.entity.AuditEventType;
import tn.esprit.simulateurbackend.service.AuditService;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class RateLimitService {

    private final ConcurrentHashMap<String, Bucket>
            buckets =
            new ConcurrentHashMap<>();


    private final AuditService auditService;


    public RateLimitService(
            AuditService auditService
    ) {

        this.auditService =
                auditService;
    }


    /*
     * ==========================================
     * LOGIN
     *
     * 5 tentatives / minute / IP
     * ==========================================
     */

    public void checkLogin(
            HttpServletRequest request
    ) {

        check(
                "login",
                resolveClientIp(request),
                5,
                Duration.ofMinutes(1),
                request
        );
    }


    /*
     * ==========================================
     * REGISTER
     *
     * 3 tentatives / 10 minutes / IP
     * ==========================================
     */

    public void checkRegister(
            HttpServletRequest request
    ) {

        check(
                "register",
                resolveClientIp(request),
                3,
                Duration.ofMinutes(10),
                request
        );
    }


    /*
     * ==========================================
     * FORGOT PASSWORD
     *
     * 3 demandes / 15 minutes / IP
     * ==========================================
     */

    public void checkForgotPassword(
            HttpServletRequest request
    ) {

        check(
                "forgot-password",
                resolveClientIp(request),
                3,
                Duration.ofMinutes(15),
                request
        );
    }


    /*
     * ==========================================
     * GENERIC CHECK
     * ==========================================
     */

    private void check(
            String action,
            String clientIp,
            long capacity,
            Duration refillDuration,
            HttpServletRequest request
    ) {

        String key =
                action
                        + ":"
                        + clientIp;


        Bucket bucket =
                buckets.computeIfAbsent(

                        key,

                        ignored ->
                                createBucket(
                                        capacity,
                                        refillDuration
                                )

                );


        boolean allowed =
                bucket.tryConsume(1);


        if (!allowed) {

            /*
             * =========================================
             * AUDIT : RATE LIMIT
             * =========================================
             */

            auditService.log(
                    AuditEventType.RATE_LIMIT_EXCEEDED,
                    null,
                    null,
                    null,
                    false,
                    "Limite dépassée pour l'action : "
                            + action,
                    request
            );


            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de tentatives. Veuillez réessayer plus tard."
            );
        }

    }


    /*
     * ==========================================
     * CREATE BUCKET
     * ==========================================
     */

    private Bucket createBucket(
            long capacity,
            Duration duration
    ) {

        return Bucket.builder()

                .addLimit(limit ->
                        limit
                                .capacity(
                                        capacity
                                )

                                .refillIntervally(
                                        capacity,
                                        duration
                                )
                )

                .build();
    }


    /*
     * ==========================================
     * CLIENT IP
     * ==========================================
     */

    private String resolveClientIp(
            HttpServletRequest request
    ) {

        String ip =
                request.getRemoteAddr();


        if (
                ip == null ||
                        ip.isBlank()
        ) {

            return "unknown";
        }


        return ip;
    }

}