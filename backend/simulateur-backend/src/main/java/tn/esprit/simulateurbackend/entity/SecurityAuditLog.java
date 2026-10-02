package tn.esprit.simulateurbackend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "security_audit_logs",
        indexes = {
                @Index(
                        name = "idx_security_audit_created_at",
                        columnList = "created_at"
                ),
                @Index(
                        name = "idx_security_audit_event_type",
                        columnList = "event_type"
                ),
                @Index(
                        name = "idx_security_audit_email",
                        columnList = "email"
                )
        }
)
public class SecurityAuditLog {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;


    @Enumerated(EnumType.STRING)
    @Column(
            name = "event_type",
            nullable = false,
            length = 50
    )
    private AuditEventType eventType;


    @Column(
            name = "user_id"
    )
    private Long userId;


    @Column(
            name = "email",
            length = 150
    )
    private String email;


    @Column(
            name = "actor_email",
            length = 150
    )
    private String actorEmail;


    @Column(
            name = "ip_address",
            length = 64
    )
    private String ipAddress;


    @Column(
            name = "user_agent",
            length = 500
    )
    private String userAgent;


    @Column(
            name = "endpoint",
            length = 255
    )
    private String endpoint;


    @Column(
            name = "success",
            nullable = false
    )
    private boolean success;


    @Column(
            name = "details",
            length = 1000
    )
    private String details;


    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;


    @PrePersist
    public void prePersist() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }


    public Long getId() {
        return id;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public void setEventType(
            AuditEventType eventType
    ) {
        this.eventType = eventType;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(
            Long userId
    ) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(
            String email
    ) {
        this.email = email;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public void setActorEmail(
            String actorEmail
    ) {
        this.actorEmail = actorEmail;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(
            String ipAddress
    ) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(
            String userAgent
    ) {
        this.userAgent = userAgent;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(
            String endpoint
    ) {
        this.endpoint = endpoint;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(
            boolean success
    ) {
        this.success = success;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(
            String details
    ) {
        this.details = details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}