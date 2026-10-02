package tn.esprit.simulateurbackend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_identities",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_identity_provider_subject",
                        columnNames = {
                                "provider",
                                "provider_subject"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_user_identity_user_id",
                        columnList = "user_id"
                ),

                @Index(
                        name = "idx_user_identity_provider",
                        columnList = "provider"
                )
        }
)
public class UserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;


    @Enumerated(EnumType.STRING)
    @Column(
            name = "provider",
            nullable = false,
            length = 30
    )
    private AuthProvider provider;


    @Column(
            name = "provider_subject",
            nullable = false,
            length = 255
    )
    private String providerSubject;


    @Column(
            name = "email_at_provider",
            length = 150
    )
    private String emailAtProvider;


    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;


    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }


    public Long getId() {
        return id;
    }


    public User getUser() {
        return user;
    }


    public void setUser(
            User user
    ) {
        this.user = user;
    }


    public AuthProvider getProvider() {
        return provider;
    }


    public void setProvider(
            AuthProvider provider
    ) {
        this.provider = provider;
    }


    public String getProviderSubject() {
        return providerSubject;
    }


    public void setProviderSubject(
            String providerSubject
    ) {
        this.providerSubject =
                providerSubject;
    }


    public String getEmailAtProvider() {
        return emailAtProvider;
    }


    public void setEmailAtProvider(
            String emailAtProvider
    ) {
        this.emailAtProvider =
                emailAtProvider;
    }


    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}